package com.example.nifi.controller;

import com.example.nifi.model.*;
import com.example.nifi.service.CallbackService;
import com.example.nifi.service.FlowCompletionMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Контроллер для управления мониторингом завершения потоков NiFi.
 * 
 * АРХИТЕКТУРА ИНТЕГРАЦИИ:
 * 1. NiFi запускает процессорную группу по расписанию
 * 2. Сразу после старта вызывается InvokeHTTP → POST /api/nifi/flow-monitor/start
 * 3. Сервис создаёт изолированную сессию мониторинга с чистым состоянием
 * 4. Сервис отслеживает появление FlowFile (признак старта) и опустошение очередей
 * 5. При завершении отправляет результат на callback URL в NiFi
 */
@RestController
@RequestMapping("/api/nifi/flow-monitor")
@CrossOrigin(origins = "*")
public class FlowCompletionController {

    private static final Logger logger = LoggerFactory.getLogger(FlowCompletionController.class);

    private final FlowCompletionMonitorService monitorService;
    private final CallbackService callbackService;
    
    // Пул потоков для асинхронного мониторинга
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Хранилище активных сессий мониторинга
    private final Map<String, MonitoringSession> activeSessions = new ConcurrentHashMap<>();

    public FlowCompletionController(FlowCompletionMonitorService monitorService, 
                                    CallbackService callbackService) {
        this.monitorService = monitorService;
        this.callbackService = callbackService;
    }

    /**
     * Внутренний класс для хранения состояния сессии мониторинга
     */
    private static class MonitoringSession {
        final String correlationId;
        final String flowName;
        final String callbackUrl;
        final Map<String, String> metadata;
        final long startTime;
        Future<FlowMonitoringResult> future;
        
        MonitoringSession(String correlationId, String flowName, String callbackUrl, Map<String, String> metadata) {
            this.correlationId = correlationId;
            this.flowName = flowName;
            this.callbackUrl = callbackUrl;
            this.metadata = metadata;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * ЗАПУСК МОНИТОРИНГА ИЗ NIFI
     * Вызывается из NiFi через InvokeHTTP сразу после стартового блока по расписанию.
     * 
     * POST /api/nifi/flow-monitor/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startMonitoringFromNiFi(
            @RequestBody FlowMonitoringRequest request) {
        
        logger.info("=== Starting monitoring from NiFi: processGroupId={}, correlationId={}, flowName={} ===", 
                   request.getProcessGroupId(), request.getCorrelationId(), request.getFlowName());
        
        String processGroupId = request.getProcessGroupId();
        
        if (processGroupId == null || processGroupId.isEmpty()) {
            return buildErrorResponse("processGroupId is required", HttpStatus.BAD_REQUEST);
        }
        
        // Проверяем, не запущен ли уже мониторинг для этой группы
        if (activeSessions.containsKey(processGroupId)) {
            logger.warn("Monitoring already running for processGroupId={}", processGroupId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ALREADY_RUNNING");
            response.put("message", "Мониторинг уже запущен для этой процессорной группы");
            response.put("processGroupId", processGroupId);
            return ResponseEntity.ok(response);
        }
        
        // Создаём конфигурацию из запроса
        FlowCompletionConfig config = request.toConfig();
        
        // Создаём сессию мониторинга
        MonitoringSession session = new MonitoringSession(
            request.getCorrelationId() != null ? request.getCorrelationId() : generateCorrelationId(),
            request.getFlowName(),
            request.getCallbackUrl(),
            request.getMetadata()
        );
        
        try {
            // Запускаем асинхронную задачу мониторинга
            Future<FlowMonitoringResult> future = executorService.submit(() -> {
                return runMonitoringSession(processGroupId, session, config);
            });
            
            session.future = future;
            activeSessions.put(processGroupId, session);
            
            logger.info("Monitoring session created: correlationId={}, processGroupId={}", 
                       session.correlationId, processGroupId);
            
            // Возвращаем ответ в NiFi
            Map<String, Object> response = new HashMap<>();
            response.put("status", "STARTED");
            response.put("message", "Мониторинг запущен. Результат будет отправлен на callback URL после завершения.");
            response.put("correlationId", session.correlationId);
            response.put("processGroupId", processGroupId);
            response.put("config", Map.of(
                "maxWaitTimeMs", config.getMaxWaitTimeMs(),
                "checkIntervalMs", config.getCheckIntervalMs(),
                "consecutiveEmptyChecksRequired", config.getConsecutiveEmptyChecksRequired()
            ));
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error starting monitoring session", e);
            activeSessions.remove(processGroupId);
            return buildErrorResponse("Failed to start monitoring: " + e.getMessage(), 
                                     HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Выполнить сессию мониторинга от начала до конца
     */
    private FlowMonitoringResult runMonitoringSession(String processGroupId, 
                                                       MonitoringSession session,
                                                       FlowCompletionConfig config) {
        logger.info("Running monitoring session: correlationId={}, processGroupId={}", 
                   session.correlationId, processGroupId);
        
        FlowMonitoringResult result = null;
        
        try {
            // Запускаем мониторинг через сервис
            FlowCompletionStatus status = monitorService.waitForFlowCompletionWithTracking(
                processGroupId, config, session.correlationId);
            
            // Создаём результат
            result = FlowMonitoringResult.fromStatus(
                status,
                session.correlationId,
                session.flowName,
                session.startTime,
                monitorService.getPeakFlowFileCount(processGroupId),
                monitorService.getConsecutiveEmptyChecks(processGroupId),
                session.metadata
            );
            
            // Отправляем callback в NiFi если указан URL
            if (session.callbackUrl != null && !session.callbackUrl.isEmpty()) {
                boolean callbackSent = callbackService.sendCallbackWithRetry(
                    session.callbackUrl, result, 3);
                
                if (!callbackSent) {
                    logger.error("Failed to send callback after retries. Result available via API.");
                }
            } else {
                logger.info("No callback URL provided. Result available via GET /status endpoint.");
            }
            
            return result;
            
        } catch (TimeoutException e) {
            logger.warn("Monitoring timeout: correlationId={}", session.correlationId);
            result = createTimeoutResult(session, e.getMessage());
            sendCallbackIfConfigured(session, result);
            return result;
            
        } catch (InterruptedException e) {
            logger.info("Monitoring interrupted: correlationId={}", session.correlationId);
            Thread.currentThread().interrupt();
            result = createInterruptedResult(session);
            sendCallbackIfConfigured(session, result);
            return result;
            
        } catch (Exception e) {
            logger.error("Monitoring error: correlationId={}", session.correlationId, e);
            result = createErrorResult(session, e.getMessage());
            sendCallbackIfConfigured(session, result);
            return result;
            
        } finally {
            // Очищаем сессию
            activeSessions.remove(processGroupId);
            logger.info("Monitoring session finished: correlationId={}, processGroupId={}", 
                       session.correlationId, processGroupId);
        }
    }

    /**
     * Отправить callback если настроен
     */
    private void sendCallbackIfConfigured(MonitoringSession session, FlowMonitoringResult result) {
        if (session.callbackUrl != null && !session.callbackUrl.isEmpty()) {
            callbackService.sendCallbackWithRetry(session.callbackUrl, result, 3);
        }
    }

    /**
     * Создать результат при таймауте
     */
    private FlowMonitoringResult createTimeoutResult(MonitoringSession session, String message) {
        FlowMonitoringResult result = new FlowMonitoringResult();
        result.setCorrelationId(session.correlationId);
        result.setProcessGroupId(activeSessions.containsKey(getProcessGroupIdByCorrelation(session.correlationId)) 
                                 ? getProcessGroupIdByCorrelation(session.correlationId) : "unknown");
        result.setFlowName(session.flowName);
        result.setCompleted(false);
        result.setStatus("TIMEOUT");
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        result.setMetadata(session.metadata);
        return result;
    }

    /**
     * Создать результат при прерывании
     */
    private FlowMonitoringResult createInterruptedResult(MonitoringSession session) {
        FlowMonitoringResult result = new FlowMonitoringResult();
        result.setCorrelationId(session.correlationId);
        result.setFlowName(session.flowName);
        result.setCompleted(false);
        result.setStatus("INTERRUPTED");
        result.setMessage("Мониторинг был прерван");
        result.setTimestamp(System.currentTimeMillis());
        result.setMetadata(session.metadata);
        return result;
    }

    /**
     * Создать результат при ошибке
     */
    private FlowMonitoringResult createErrorResult(MonitoringSession session, String errorMessage) {
        FlowMonitoringResult result = new FlowMonitoringResult();
        result.setCorrelationId(session.correlationId);
        result.setFlowName(session.flowName);
        result.setCompleted(false);
        result.setStatus("ERROR");
        result.setMessage("Ошибка: " + errorMessage);
        result.setTimestamp(System.currentTimeMillis());
        result.setMetadata(session.metadata);
        return result;
    }

    /**
     * Получить статус мониторинга по processGroupId
     * GET /api/nifi/flow-monitor/status/{processGroupId}
     */
    @GetMapping("/status/{processGroupId}")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus(
            @PathVariable String processGroupId) {
        
        logger.debug("Getting monitoring status for process group: {}", processGroupId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("processGroupId", processGroupId);
        
        MonitoringSession session = activeSessions.get(processGroupId);
        
        if (session == null) {
            response.put("monitoring", false);
            response.put("message", "Мониторинг не запущен");
            return ResponseEntity.ok(response);
        }
        
        response.put("monitoring", true);
        response.put("correlationId", session.correlationId);
        response.put("flowName", session.flowName);
        response.put("startTime", session.startTime);
        response.put("completed", session.future != null && session.future.isDone());
        
        if (session.future != null && session.future.isDone()) {
            try {
                FlowMonitoringResult result = session.future.get();
                response.put("result", result);
                response.put("message", "Мониторинг завершен");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.put("error", "Прервано");
            } catch (ExecutionException e) {
                response.put("error", e.getCause().getMessage());
            }
        } else {
            response.put("message", "Мониторинг выполняется");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Остановить мониторинг
     * POST /api/nifi/flow-monitor/stop/{processGroupId}
     */
    @PostMapping("/stop/{processGroupId}")
    public ResponseEntity<Map<String, Object>> stopMonitoring(
            @PathVariable String processGroupId) {
        
        logger.info("Stopping monitoring for process group: {}", processGroupId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("processGroupId", processGroupId);
        
        MonitoringSession session = activeSessions.remove(processGroupId);
        
        if (session == null) {
            monitorService.stopMonitoring(processGroupId);
            response.put("status", "NOT_FOUND");
            response.put("message", "Активный мониторинг не найден");
            return ResponseEntity.ok(response);
        }
        
        if (session.future != null) {
            session.future.cancel(true);
        }
        monitorService.stopMonitoring(processGroupId);
        
        response.put("status", "STOPPED");
        response.put("correlationId", session.correlationId);
        response.put("message", "Мониторинг остановлен");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получить список всех активных сессий
     * GET /api/nifi/flow-monitor/active
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveSessions() {
        logger.debug("Getting active monitoring sessions");
        
        Map<String, Object> response = new HashMap<>();
        response.put("count", activeSessions.size());
        
        Map<String, Map<String, Object>> sessions = new HashMap<>();
        for (Map.Entry<String, MonitoringSession> entry : activeSessions.entrySet()) {
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("correlationId", entry.getValue().correlationId);
            sessionInfo.put("flowName", entry.getValue().flowName);
            sessionInfo.put("startTime", entry.getValue().startTime);
            sessionInfo.put("running", entry.getValue().future != null && !entry.getValue().future.isDone());
            sessions.put(entry.getKey(), sessionInfo);
        }
        
        response.put("sessions", sessions);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Построить ответ об ошибке
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Сгенерировать уникальный correlation ID
     */
    private String generateCorrelationId() {
        return "flow-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Найти processGroupId по correlationId
     */
    private String getProcessGroupIdByCorrelation(String correlationId) {
        for (Map.Entry<String, MonitoringSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().correlationId.equals(correlationId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Очистка ресурсов при уничтожении бина
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up {} active monitoring sessions", activeSessions.size());
        
        for (MonitoringSession session : activeSessions.values()) {
            if (session.future != null) {
                session.future.cancel(true);
            }
            monitorService.stopMonitoring(getProcessGroupIdByCorrelation(session.correlationId));
        }
        
        activeSessions.clear();
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

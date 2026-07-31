package com.example.nifi.controller;

import com.example.nifi.model.FlowCompletionConfig;
import com.example.nifi.model.FlowCompletionStatus;
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
 * Контроллер для управления мониторингом завершения потоков NiFi
 */
@RestController
@RequestMapping("/api/nifi/flow-monitor")
@CrossOrigin(origins = "*")
public class FlowCompletionController {

    private static final Logger logger = LoggerFactory.getLogger(FlowCompletionController.class);

    private final FlowCompletionMonitorService monitorService;
    
    // Пул потоков для асинхронного мониторинга
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Хранилище будущих результатов мониторинга
    private final Map<String, Future<FlowCompletionStatus>> activeMonitors = new ConcurrentHashMap<>();

    public FlowCompletionController(FlowCompletionMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * Однократная проверка статуса завершения потока
     * POST /api/nifi/flow-monitor/{processGroupId}/check
     */
    @PostMapping("/{processGroupId}/check")
    public ResponseEntity<FlowCompletionStatus> checkFlowCompletion(
            @PathVariable String processGroupId,
            @RequestBody(required = false) FlowCompletionConfig config) {
        
        logger.info("Checking flow completion for process group: {}", processGroupId);
        
        if (config == null) {
            config = createDefaultConfig();
        }
        
        try {
            FlowCompletionStatus status = monitorService.checkFlowCompletion(processGroupId, config);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error checking flow completion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Запустить асинхронный мониторинг завершения потока
     * POST /api/nifi/flow-monitor/{processGroupId}/start-monitoring
     */
    @PostMapping("/{processGroupId}/start-monitoring")
    public ResponseEntity<Map<String, Object>> startMonitoring(
            @PathVariable String processGroupId,
            @RequestBody(required = false) FlowCompletionConfig config) {
        
        logger.info("Starting async monitoring for process group: {}", processGroupId);
        
        // Проверяем, не запущен ли уже мониторинг
        if (activeMonitors.containsKey(processGroupId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ALREADY_RUNNING");
            response.put("message", "Мониторинг уже запущен для этой процессорной группы");
            response.put("processGroupId", processGroupId);
            return ResponseEntity.ok(response);
        }
        
        if (config == null) {
            config = createDefaultConfig();
        }
        
        final FlowCompletionConfig finalConfig = config;
        
        try {
            // Запускаем асинхронную задачу
            Future<FlowCompletionStatus> future = executorService.submit(() -> {
                try {
                    return monitorService.waitForFlowCompletion(processGroupId, finalConfig);
                } catch (TimeoutException e) {
                    logger.warn("Monitoring timeout for process group: {}", processGroupId);
                    FlowCompletionStatus timeoutStatus = new FlowCompletionStatus();
                    timeoutStatus.setProcessGroupId(processGroupId);
                    timeoutStatus.setCompleted(false);
                    timeoutStatus.setStatus("TIMEOUT");
                    timeoutStatus.setMessage(e.getMessage());
                    return timeoutStatus;
                } catch (InterruptedException e) {
                    logger.info("Monitoring interrupted for process group: {}", processGroupId);
                    Thread.currentThread().interrupt();
                    FlowCompletionStatus interruptedStatus = new FlowCompletionStatus();
                    interruptedStatus.setProcessGroupId(processGroupId);
                    interruptedStatus.setCompleted(false);
                    interruptedStatus.setStatus("INTERRUPTED");
                    interruptedStatus.setMessage("Мониторинг был прерван");
                    return interruptedStatus;
                }
            });
            
            activeMonitors.put(processGroupId, future);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "STARTED");
            response.put("message", "Мониторинг запущен");
            response.put("processGroupId", processGroupId);
            response.put("config", Map.of(
                "maxWaitTimeMs", config.getMaxWaitTimeMs(),
                "checkIntervalMs", config.getCheckIntervalMs(),
                "consecutiveEmptyChecksRequired", config.getConsecutiveEmptyChecksRequired()
            ));
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error starting monitoring", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Получить статус мониторинга
     * GET /api/nifi/flow-monitor/{processGroupId}/status
     */
    @GetMapping("/{processGroupId}/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus(
            @PathVariable String processGroupId) {
        
        logger.debug("Getting monitoring status for process group: {}", processGroupId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("processGroupId", processGroupId);
        
        Future<FlowCompletionStatus> future = activeMonitors.get(processGroupId);
        
        if (future == null) {
            response.put("monitoring", false);
            response.put("message", "Мониторинг не запущен");
            return ResponseEntity.ok(response);
        }
        
        response.put("monitoring", true);
        response.put("completed", future.isDone());
        
        if (future.isDone()) {
            try {
                FlowCompletionStatus status = future.get();
                response.put("status", status);
                response.put("message", "Мониторинг завершен");
                
                // Удаляем завершенный мониторинг
                activeMonitors.remove(processGroupId);
                
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
     * POST /api/nifi/flow-monitor/{processGroupId}/stop
     */
    @PostMapping("/{processGroupId}/stop")
    public ResponseEntity<Map<String, Object>> stopMonitoring(
            @PathVariable String processGroupId) {
        
        logger.info("Stopping monitoring for process group: {}", processGroupId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("processGroupId", processGroupId);
        
        Future<FlowCompletionStatus> future = activeMonitors.remove(processGroupId);
        
        if (future == null) {
            // Пробуем остановить через сервис
            monitorService.stopMonitoring(processGroupId);
            response.put("status", "NOT_FOUND");
            response.put("message", "Активный мониторинг не найден, но отправлен сигнал остановки");
            return ResponseEntity.ok(response);
        }
        
        future.cancel(true);
        monitorService.stopMonitoring(processGroupId);
        
        response.put("status", "STOPPED");
        response.put("message", "Мониторинг остановлен");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получить список всех активных мониторингов
     * GET /api/nifi/flow-monitor/active
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveMonitors() {
        logger.debug("Getting active monitors");
        
        Map<String, Object> response = new HashMap<>();
        response.put("count", activeMonitors.size());
        response.put("monitors", activeMonitors.keySet());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Создать конфигурацию по умолчанию
     */
    private FlowCompletionConfig createDefaultConfig() {
        FlowCompletionConfig config = new FlowCompletionConfig();
        config.setMaxWaitTimeMs(3600000L); // 1 час
        config.setCheckIntervalMs(5000L); // 5 секунд
        config.setEmptyQueueThreshold(0);
        config.setEmptyQueueSizeThreshold(0L);
        config.setConsecutiveEmptyChecksRequired(3);
        config.setConsiderOnlyActiveProcessors(true);
        return config;
    }

    /**
     * Очистка ресурсов при уничтожении бина
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up active monitors");
        
        for (Map.Entry<String, Future<FlowCompletionStatus>> entry : activeMonitors.entrySet()) {
            entry.getValue().cancel(true);
            monitorService.stopMonitoring(entry.getKey());
        }
        
        activeMonitors.clear();
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

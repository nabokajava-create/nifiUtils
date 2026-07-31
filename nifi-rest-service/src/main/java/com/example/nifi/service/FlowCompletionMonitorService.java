package com.example.nifi.service;

import com.example.nifi.config.NifiApiProperties;
import com.example.nifi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис для мониторинга завершения потоков NiFi на основе анализа очередей
 */
@Service
public class FlowCompletionMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(FlowCompletionMonitorService.class);

    private final RestTemplate restTemplate;
    private final NifiApiProperties nifiApiProperties;
    
    // Хранилище состояний мониторинга для каждого process group
    private final Map<String, MonitoringState> monitoringStates = new ConcurrentHashMap<>();

    public FlowCompletionMonitorService(RestTemplate restTemplate, NifiApiProperties nifiApiProperties) {
        this.restTemplate = restTemplate;
        this.nifiApiProperties = nifiApiProperties;
    }

    /**
     * Внутренний класс для хранения состояния мониторинга
     */
    private static class MonitoringState {
        long startTime;
        int consecutiveEmptyChecks;
        boolean isMonitoring;
        List<QueueStatus> lastQueueStatuses;

        MonitoringState() {
            this.startTime = System.currentTimeMillis();
            this.consecutiveEmptyChecks = 0;
            this.isMonitoring = true;
            this.lastQueueStatuses = new ArrayList<>();
        }
    }

    /**
     * Проверить статус завершения потока в процессорной группе
     * 
     * @param processGroupId ID процессорной группы
     * @param config конфигурация мониторинга
     * @return статус завершения потока
     */
    public FlowCompletionStatus checkFlowCompletion(String processGroupId, FlowCompletionConfig config) {
        logger.debug("Checking flow completion for process group: {}", processGroupId);
        
        try {
            // Получаем информацию о соединениях (очередях)
            List<Map<String, Object>> connections = getConnections(processGroupId);
            
            // Получаем информацию о процессорах
            List<Map<String, Object>> processors = getProcessors(processGroupId);
            
            // Анализируем очереди
            List<QueueStatus> queueStatuses = analyzeQueues(connections, config);
            
            // Считаем агрегированные метрики
            long totalQueueSize = queueStatuses.stream()
                    .mapToLong(QueueStatus::getQueueSize)
                    .sum();
            
            int totalFlowFileCount = queueStatuses.stream()
                    .mapToInt(QueueStatus::getFlowFileCount)
                    .sum();
            
            // Считаем активные процессоры
            int activeProcessorCount = countActiveProcessors(processors);
            int totalProcessorCount = processors.size();
            
            // Определяем статус завершения
            boolean completed = evaluateCompletion(queueStatuses, activeProcessorCount, config);
            String status = determineStatus(completed, queueStatuses, activeProcessorCount, totalProcessorCount);
            
            // Создаём результат
            FlowCompletionStatus result = new FlowCompletionStatus();
            result.setProcessGroupId(processGroupId);
            result.setProcessGroupName(getProcessGroupName(processGroupId));
            result.setCompleted(completed);
            result.setStatus(status);
            result.setTotalQueueSize(totalQueueSize);
            result.setTotalFlowFileCount(totalFlowFileCount);
            result.setActiveProcessorCount(activeProcessorCount);
            result.setTotalProcessorCount(totalProcessorCount);
            result.setQueueStatuses(queueStatuses);
            result.setCheckTimestamp(System.currentTimeMillis());
            
            if (completed) {
                result.setMessage("Поток завершен: все очереди пусты и нет активных процессоров");
            } else {
                result.setMessage(String.format(
                    "Поток выполняется: %d файлов в очередях, %d/%d активных процессоров",
                    totalFlowFileCount, activeProcessorCount, totalProcessorCount
                ));
            }
            
            logger.info("Flow completion check for {}: completed={}, status={}", 
                       processGroupId, completed, status);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error checking flow completion for {}: {}", processGroupId, e.getMessage());
            FlowCompletionStatus errorResult = new FlowCompletionStatus();
            errorResult.setProcessGroupId(processGroupId);
            errorResult.setCompleted(false);
            errorResult.setStatus("ERROR");
            errorResult.setMessage("Ошибка проверки: " + e.getMessage());
            errorResult.setCheckTimestamp(System.currentTimeMillis());
            return errorResult;
        }
    }

    /**
     * Запустить мониторинг завершения потока с периодическими проверками
     * 
     * @param processGroupId ID процессорной группы
     * @param config конфигурация мониторинга
     * @return финальный статус завершения потока
     * @throws InterruptedException если мониторинг был прерван
     * @throws TimeoutException если превышено максимальное время ожидания
     */
    public FlowCompletionStatus waitForFlowCompletion(String processGroupId, FlowCompletionConfig config) 
            throws InterruptedException, java.util.concurrent.TimeoutException {
        
        logger.info("Starting flow completion monitoring for process group: {}", processGroupId);
        
        MonitoringState state = new MonitoringState();
        monitoringStates.put(processGroupId, state);
        
        long timeout = config.getMaxWaitTimeMs();
        long interval = config.getCheckIntervalMs();
        long startTime = System.currentTimeMillis();
        
        try {
            while (state.isMonitoring) {
                // Проверяем таймаут
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeout) {
                    state.isMonitoring = false;
                    FlowCompletionStatus timeoutStatus = checkFlowCompletion(processGroupId, config);
                    timeoutStatus.setStatus("TIMEOUT");
                    timeoutStatus.setMessage("Превышено максимальное время ожидания: " + timeout + " мс");
                    throw new java.util.concurrent.TimeoutException("Flow completion timeout after " + elapsed + " ms");
                }
                
                // Выполняем проверку
                FlowCompletionStatus status = checkFlowCompletion(processGroupId, config);
                
                // Если поток завершен, возвращаем результат
                if (status.isCompleted()) {
                    state.isMonitoring = false;
                    logger.info("Flow completion detected for process group: {}", processGroupId);
                    return status;
                }
                
                // Обновляем состояние
                state.lastQueueStatuses = status.getQueueStatuses();
                
                // Проверяем счётчик последовательных пустых проверок
                boolean allQueuesEmpty = status.getQueueStatuses().stream()
                        .allMatch(q -> q.getFlowFileCount() <= config.getEmptyQueueThreshold() && 
                                      q.getQueueSize() <= config.getEmptyQueueSizeThreshold());
                
                if (allQueuesEmpty) {
                    state.consecutiveEmptyChecks++;
                    logger.debug("Consecutive empty checks: {}/{}", 
                                state.consecutiveEmptyChecks, config.getConsecutiveEmptyChecksRequired());
                    
                    if (state.consecutiveEmptyChecks >= config.getConsecutiveEmptyChecksRequired()) {
                        // Дополнительная проверка: нет ли активных процессоров
                        if (status.getActiveProcessorCount() == 0 || !config.isConsiderOnlyActiveProcessors()) {
                            status.setCompleted(true);
                            status.setStatus("COMPLETED");
                            status.setMessage("Все очереди пусты в течение " + 
                                            (state.consecutiveEmptyChecks * interval / 1000) + " секунд");
                            state.isMonitoring = false;
                            return status;
                        }
                    }
                } else {
                    state.consecutiveEmptyChecks = 0;
                }
                
                // Ждём следующую проверку
                Thread.sleep(interval);
            }
            
            return checkFlowCompletion(processGroupId, config);
            
        } finally {
            monitoringStates.remove(processGroupId);
            logger.info("Flow completion monitoring finished for process group: {}", processGroupId);
        }
    }

    /**
     * Остановить мониторинг для указанной процессорной группы
     */
    public void stopMonitoring(String processGroupId) {
        MonitoringState state = monitoringStates.get(processGroupId);
        if (state != null) {
            state.isMonitoring = false;
            logger.info("Monitoring stopped for process group: {}", processGroupId);
        }
    }

    /**
     * Получить текущее состояние мониторинга
     */
    public boolean isMonitoring(String processGroupId) {
        MonitoringState state = monitoringStates.get(processGroupId);
        return state != null && state.isMonitoring;
    }

    /**
     * Проанализировать очереди и создать список статусов
     */
    private List<QueueStatus> analyzeQueues(List<Map<String, Object>> connections, FlowCompletionConfig config) {
        List<QueueStatus> queueStatuses = new ArrayList<>();
        
        for (Map<String, Object> connection : connections) {
            Map<String, Object> component = (Map<String, Object>) connection.get("component");
            if (component == null) continue;
            
            String connectionId = (String) connection.get("id");
            String sourceName = extractName(component, "source");
            String destinationName = extractName(component, "destination");
            
            // Проверяем паттерны игнорирования
            if (shouldIgnoreQueue(sourceName, destinationName, config)) {
                logger.debug("Ignoring queue: {} -> {}", sourceName, destinationName);
                continue;
            }
            
            Map<String, Object> flowFileStats = (Map<String, Object>) component.get("flowFileCount");
            Map<String, Object> queueSizeStats = (Map<String, Object>) component.get("queueSize");
            
            int flowFileCount = 0;
            long queueSize = 0L;
            long maxQueueSize = 0L;
            
            if (flowFileStats != null && flowFileStats.containsKey("count")) {
                Number count = (Number) flowFileStats.get("count");
                flowFileCount = count != null ? count.intValue() : 0;
            }
            
            if (queueSizeStats != null) {
                Number size = (Number) queueSizeStats.get("size");
                queueSize = size != null ? size.longValue() : 0L;
                
                Number maxSize = (Number) queueSizeStats.get("maxSize");
                maxQueueSize = maxSize != null ? maxSize.longValue() : 0L;
            }
            
            QueueStatus queueStatus = new QueueStatus(
                connectionId, sourceName, destinationName, 
                flowFileCount, queueSize, maxQueueSize
            );
            
            queueStatuses.add(queueStatus);
        }
        
        return queueStatuses;
    }

    /**
     * Оценить завершение потока на основе состояния очередей и процессоров
     */
    private boolean evaluateCompletion(List<QueueStatus> queueStatuses, int activeProcessorCount, 
                                       FlowCompletionConfig config) {
        // Проверяем, что все очереди пусты (с учётом порогов)
        boolean allQueuesEmpty = queueStatuses.isEmpty() || queueStatuses.stream()
                .allMatch(q -> q.getFlowFileCount() <= config.getEmptyQueueThreshold() && 
                              q.getQueueSize() <= config.getEmptyQueueSizeThreshold());
        
        if (!allQueuesEmpty) {
            return false;
        }
        
        // Если нужно учитывать активные процессоры
        if (config.isConsiderOnlyActiveProcessors()) {
            return activeProcessorCount == 0;
        }
        
        return true;
    }

    /**
     * Определить текстовый статус
     */
    private String determineStatus(boolean completed, List<QueueStatus> queueStatuses, 
                                   int activeProcessorCount, int totalProcessorCount) {
        if (completed) {
            return "COMPLETED";
        }
        
        if (activeProcessorCount == 0 && totalProcessorCount > 0) {
            return "STOPPED";
        }
        
        boolean hasData = queueStatuses.stream()
                .anyMatch(q -> q.getFlowFileCount() > 0 || q.getQueueSize() > 0);
        
        if (hasData) {
            return "RUNNING";
        }
        
        return "IDLE";
    }

    /**
     * Извлечь имя из компонента (источник или назначение)
     */
    private String extractName(Map<String, Object> component, String key) {
        Object obj = component.get(key);
        if (obj instanceof Map) {
            Map<String, Object> nested = (Map<String, Object>) obj;
            String name = (String) nested.get("name");
            return name != null ? name : "unknown";
        }
        return "unknown";
    }

    /**
     * Проверить, должна ли очередь быть проигнорирована
     */
    private boolean shouldIgnoreQueue(String sourceName, String destinationName, FlowCompletionConfig config) {
        String[] patterns = config.getIgnoredQueuePatterns();
        if (patterns == null || patterns.length == 0) {
            return false;
        }
        
        for (String pattern : patterns) {
            if (sourceName.matches(pattern) || destinationName.matches(pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Посчитать количество активных процессоров
     */
    private int countActiveProcessors(List<Map<String, Object>> processors) {
        int count = 0;
        
        for (Map<String, Object> processor : processors) {
            Map<String, Object> component = (Map<String, Object>) processor.get("component");
            if (component != null) {
                String state = (String) component.get("state");
                if ("RUNNING".equals(state)) {
                    count++;
                }
            }
        }
        
        return count;
    }

    /**
     * Получить имя процессорной группы
     */
    private String getProcessGroupName(String processGroupId) {
        try {
            String url = nifiApiProperties.getBaseUrl() + "/process-groups/" + processGroupId;
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createEntity(),
                Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("component")) {
                Map<String, Object> component = (Map<String, Object>) body.get("component");
                String name = (String) component.get("name");
                return name != null ? name : processGroupId;
            }
        } catch (Exception e) {
            logger.warn("Could not get process group name for {}: {}", processGroupId, e.getMessage());
        }
        
        return processGroupId;
    }

    /**
     * Получить соединения для процессорной группы
     */
    private List<Map<String, Object>> getConnections(String processGroupId) {
        String url = nifiApiProperties.getBaseUrl() + "/process-groups/" + processGroupId + "/connections";
        logger.debug("Calling NiFi API: {}", url);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            createEntity(),
            Map.class
        );
        
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("connections")) {
            return (List<Map<String, Object>>) body.get("connections");
        }
        return new ArrayList<>();
    }

    /**
     * Получить процессоры для процессорной группы
     */
    private List<Map<String, Object>> getProcessors(String processGroupId) {
        String url = nifiApiProperties.getBaseUrl() + "/process-groups/" + processGroupId + "/processors";
        logger.debug("Calling NiFi API: {}", url);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            createEntity(),
            Map.class
        );
        
        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("processors")) {
            return (List<Map<String, Object>>) body.get("processors");
        }
        return new ArrayList<>();
    }

    /**
     * Создать HTTP entity с заголовками
     */
    private HttpEntity<Void> createEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }
}

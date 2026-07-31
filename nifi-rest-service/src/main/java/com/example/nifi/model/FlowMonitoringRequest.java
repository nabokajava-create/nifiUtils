package com.example.nifi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Модель для запроса запуска мониторинга потока из NiFi.
 * Вызывается сразу после старта процессорной группы по расписанию.
 */
public class FlowMonitoringRequest {

    private String processGroupId;
    private String processGroupName;
    private String correlationId;        // Уникальный ID запуска (например, UUID или timestamp)
    private String flowName;             // Имя потока для логирования
    private Long maxWaitTimeMs;          // Максимальное время ожидания (мс)
    private Long checkIntervalMs;        // Интервал проверок (мс)
    private Integer emptyQueueThreshold; // Порог пустой очереди (кол-во файлов)
    private Long emptyQueueSizeThreshold;// Порог пустой очереди (размер в байтах)
    private Integer consecutiveEmptyChecksRequired; // Кол-во последовательных пустых проверок
    private String callbackUrl;          // URL для отправки результата (webhook)
    private Map<String, String> metadata; // Дополнительные метаданные

    public FlowMonitoringRequest() {
        this.metadata = new HashMap<>();
    }

    public String getProcessGroupId() {
        return processGroupId;
    }

    public void setProcessGroupId(String processGroupId) {
        this.processGroupId = processGroupId;
    }

    public String getProcessGroupName() {
        return processGroupName;
    }

    public void setProcessGroupName(String processGroupName) {
        this.processGroupName = processGroupName;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public Long getMaxWaitTimeMs() {
        return maxWaitTimeMs;
    }

    public void setMaxWaitTimeMs(Long maxWaitTimeMs) {
        this.maxWaitTimeMs = maxWaitTimeMs;
    }

    public Long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(Long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public Integer getEmptyQueueThreshold() {
        return emptyQueueThreshold;
    }

    public void setEmptyQueueThreshold(Integer emptyQueueThreshold) {
        this.emptyQueueThreshold = emptyQueueThreshold;
    }

    public Long getEmptyQueueSizeThreshold() {
        return emptyQueueSizeThreshold;
    }

    public void setEmptyQueueSizeThreshold(Long emptyQueueSizeThreshold) {
        this.emptyQueueSizeThreshold = emptyQueueSizeThreshold;
    }

    public Integer getConsecutiveEmptyChecksRequired() {
        return consecutiveEmptyChecksRequired;
    }

    public void setConsecutiveEmptyChecksRequired(Integer consecutiveEmptyChecksRequired) {
        this.consecutiveEmptyChecksRequired = consecutiveEmptyChecksRequired;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Создать конфигурацию мониторинга из запроса
     */
    public FlowCompletionConfig toConfig() {
        FlowCompletionConfig config = new FlowCompletionConfig();
        
        if (this.maxWaitTimeMs != null) {
            config.setMaxWaitTimeMs(this.maxWaitTimeMs);
        } else {
            config.setMaxWaitTimeMs(3600000L); // 1 час по умолчанию
        }
        
        if (this.checkIntervalMs != null) {
            config.setCheckIntervalMs(this.checkIntervalMs);
        } else {
            config.setCheckIntervalMs(5000L); // 5 секунд по умолчанию
        }
        
        if (this.emptyQueueThreshold != null) {
            config.setEmptyQueueThreshold(this.emptyQueueThreshold);
        } else {
            config.setEmptyQueueThreshold(0);
        }
        
        if (this.emptyQueueSizeThreshold != null) {
            config.setEmptyQueueSizeThreshold(this.emptyQueueSizeThreshold);
        } else {
            config.setEmptyQueueSizeThreshold(0L);
        }
        
        if (this.consecutiveEmptyChecksRequired != null) {
            config.setConsecutiveEmptyChecksRequired(this.consecutiveEmptyChecksRequired);
        } else {
            config.setConsecutiveEmptyChecksRequired(3);
        }
        
        config.setConsiderOnlyActiveProcessors(true);
        
        return config;
    }

    @Override
    public String toString() {
        return "FlowMonitoringRequest{" +
                "processGroupId='" + processGroupId + '\'' +
                ", processGroupName='" + processGroupName + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", flowName='" + flowName + '\'' +
                ", maxWaitTimeMs=" + maxWaitTimeMs +
                ", checkIntervalMs=" + checkIntervalMs +
                '}';
    }
}

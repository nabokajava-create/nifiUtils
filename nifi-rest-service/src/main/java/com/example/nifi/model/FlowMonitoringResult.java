package com.example.nifi.model;

import java.util.Map;

/**
 * Модель для ответа о результате мониторинга потока.
 * Отправляется обратно в NiFi через callback URL или возвращается по запросу.
 */
public class FlowMonitoringResult {

    private String correlationId;          // ID запуска из запроса
    private String processGroupId;
    private String processGroupName;
    private String flowName;
    
    private boolean completed;             // true - поток завершен успешно
    private String status;                 // COMPLETED, TIMEOUT, NOT_STARTED, ERROR, INTERRUPTED
    
    private long totalQueueSize;           // Общий размер очередей на момент завершения
    private int totalFlowFileCount;        // Общее кол-во FlowFile на момент завершения
    private int activeProcessorCount;      // Кол-во активных процессоров
    private int totalProcessorCount;       // Общее кол-во процессоров
    
    private Long flowDurationMs;           // Длительность выполнения (от старта до завершения)
    private Long monitoringDurationMs;     // Общая длительность мониторинга
    private Integer peakFlowFileCount;     // Пиковое кол-во FlowFile во время выполнения
    private Integer consecutiveEmptyChecks;// Кол-во последовательных пустых проверок перед завершением
    
    private String message;                // Человеко-читаемое сообщение
    private long timestamp;                // Время создания результата
    private Map<String, String> metadata;  // Метаданные из запроса

    public FlowMonitoringResult() {}

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTotalQueueSize() {
        return totalQueueSize;
    }

    public void setTotalQueueSize(long totalQueueSize) {
        this.totalQueueSize = totalQueueSize;
    }

    public int getTotalFlowFileCount() {
        return totalFlowFileCount;
    }

    public void setTotalFlowFileCount(int totalFlowFileCount) {
        this.totalFlowFileCount = totalFlowFileCount;
    }

    public int getActiveProcessorCount() {
        return activeProcessorCount;
    }

    public void setActiveProcessorCount(int activeProcessorCount) {
        this.activeProcessorCount = activeProcessorCount;
    }

    public int getTotalProcessorCount() {
        return totalProcessorCount;
    }

    public void setTotalProcessorCount(int totalProcessorCount) {
        this.totalProcessorCount = totalProcessorCount;
    }

    public Long getFlowDurationMs() {
        return flowDurationMs;
    }

    public void setFlowDurationMs(Long flowDurationMs) {
        this.flowDurationMs = flowDurationMs;
    }

    public Long getMonitoringDurationMs() {
        return monitoringDurationMs;
    }

    public void setMonitoringDurationMs(Long monitoringDurationMs) {
        this.monitoringDurationMs = monitoringDurationMs;
    }

    public Integer getPeakFlowFileCount() {
        return peakFlowFileCount;
    }

    public void setPeakFlowFileCount(Integer peakFlowFileCount) {
        this.peakFlowFileCount = peakFlowFileCount;
    }

    public Integer getConsecutiveEmptyChecks() {
        return consecutiveEmptyChecks;
    }

    public void setConsecutiveEmptyChecks(Integer consecutiveEmptyChecks) {
        this.consecutiveEmptyChecks = consecutiveEmptyChecks;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Создать результат из статуса мониторинга
     */
    public static FlowMonitoringResult fromStatus(FlowCompletionStatus status, 
                                                   String correlationId,
                                                   String flowName,
                                                   long monitoringStartTime,
                                                   Integer peakFlowFileCount,
                                                   Integer consecutiveEmptyChecks,
                                                   Map<String, String> metadata) {
        FlowMonitoringResult result = new FlowMonitoringResult();
        
        result.setCorrelationId(correlationId);
        result.setProcessGroupId(status.getProcessGroupId());
        result.setProcessGroupName(status.getProcessGroupName());
        result.setFlowName(flowName);
        result.setCompleted(status.isCompleted());
        result.setStatus(status.getStatus());
        result.setTotalQueueSize(status.getTotalQueueSize());
        result.setTotalFlowFileCount(status.getTotalFlowFileCount());
        result.setActiveProcessorCount(status.getActiveProcessorCount());
        result.setTotalProcessorCount(status.getTotalProcessorCount());
        result.setFlowDurationMs(status.getFlowDurationMs());
        result.setMonitoringDurationMs(System.currentTimeMillis() - monitoringStartTime);
        result.setPeakFlowFileCount(peakFlowFileCount);
        result.setConsecutiveEmptyChecks(consecutiveEmptyChecks);
        result.setMessage(status.getMessage());
        result.setTimestamp(System.currentTimeMillis());
        result.setMetadata(metadata);
        
        return result;
    }

    @Override
    public String toString() {
        return "FlowMonitoringResult{" +
                "correlationId='" + correlationId + '\'' +
                ", processGroupId='" + processGroupId + '\'' +
                ", completed=" + completed +
                ", status='" + status + '\'' +
                ", flowDurationMs=" + flowDurationMs +
                '}';
    }
}

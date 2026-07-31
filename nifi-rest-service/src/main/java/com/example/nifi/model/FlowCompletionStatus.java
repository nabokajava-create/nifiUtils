package com.example.nifi.model;

import java.util.List;

/**
 * Модель для представления статуса завершения потока
 */
public class FlowCompletionStatus {
    
    private String processGroupId;
    private String processGroupName;
    private boolean completed;
    private String status;
    private long totalQueueSize;
    private int totalFlowFileCount;
    private int activeProcessorCount;
    private int totalProcessorCount;
    private List<QueueStatus> queueStatuses;
    private long checkTimestamp;
    private String message;

    public FlowCompletionStatus() {}

    public FlowCompletionStatus(String processGroupId, String processGroupName, boolean completed, String status) {
        this.processGroupId = processGroupId;
        this.processGroupName = processGroupName;
        this.completed = completed;
        this.status = status;
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

    public List<QueueStatus> getQueueStatuses() {
        return queueStatuses;
    }

    public void setQueueStatuses(List<QueueStatus> queueStatuses) {
        this.queueStatuses = queueStatuses;
    }

    public long getCheckTimestamp() {
        return checkTimestamp;
    }

    public void setCheckTimestamp(long checkTimestamp) {
        this.checkTimestamp = checkTimestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "FlowCompletionStatus{" +
                "processGroupId='" + processGroupId + '\'' +
                ", processGroupName='" + processGroupName + '\'' +
                ", completed=" + completed +
                ", status='" + status + '\'' +
                ", totalQueueSize=" + totalQueueSize +
                ", totalFlowFileCount=" + totalFlowFileCount +
                ", activeProcessorCount=" + activeProcessorCount +
                ", totalProcessorCount=" + totalProcessorCount +
                '}';
    }
}

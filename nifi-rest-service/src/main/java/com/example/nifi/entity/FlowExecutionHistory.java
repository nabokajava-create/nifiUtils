package com.example.nifi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Сущность для хранения истории запусков потоков NiFi
 */
@Entity
@Table(name = "flow_execution_history")
public class FlowExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_group_id", nullable = false, length = 255)
    private String processGroupId;

    @Column(name = "process_group_name", length = 255)
    private String processGroupName;

    @Column(name = "monitoring_session_id", length = 255)
    private String monitoringSessionId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // NOT_STARTED, RUNNING, COMPLETED, TIMEOUT, ERROR

    @Column(name = "completed")
    private Boolean completed = false;

    @Column(name = "total_flow_file_count")
    private Integer totalFlowFileCount;

    @Column(name = "total_queue_size")
    private Long totalQueueSize;

    @Column(name = "active_processor_count")
    private Integer activeProcessorCount;

    @Column(name = "total_processor_count")
    private Integer totalProcessorCount;

    @Column(name = "first_activity_time")
    private LocalDateTime firstActivityTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "empty_check_count")
    private Integer emptyCheckCount;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "callback_sent")
    private Boolean callbackSent = false;

    @Column(name = "callback_sent_time")
    private LocalDateTime callbackSentTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public FlowExecutionHistory() {}

    public FlowExecutionHistory(String processGroupId, String processGroupName, String monitoringSessionId) {
        this.processGroupId = processGroupId;
        this.processGroupName = processGroupName;
        this.monitoringSessionId = monitoringSessionId;
        this.startTime = LocalDateTime.now();
        this.status = "RUNNING";
        this.completed = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getMonitoringSessionId() {
        return monitoringSessionId;
    }

    public void setMonitoringSessionId(String monitoringSessionId) {
        this.monitoringSessionId = monitoringSessionId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getTotalFlowFileCount() {
        return totalFlowFileCount;
    }

    public void setTotalFlowFileCount(Integer totalFlowFileCount) {
        this.totalFlowFileCount = totalFlowFileCount;
    }

    public Long getTotalQueueSize() {
        return totalQueueSize;
    }

    public void setTotalQueueSize(Long totalQueueSize) {
        this.totalQueueSize = totalQueueSize;
    }

    public Integer getActiveProcessorCount() {
        return activeProcessorCount;
    }

    public void setActiveProcessorCount(Integer activeProcessorCount) {
        this.activeProcessorCount = activeProcessorCount;
    }

    public Integer getTotalProcessorCount() {
        return totalProcessorCount;
    }

    public void setTotalProcessorCount(Integer totalProcessorCount) {
        this.totalProcessorCount = totalProcessorCount;
    }

    public LocalDateTime getFirstActivityTime() {
        return firstActivityTime;
    }

    public void setFirstActivityTime(LocalDateTime firstActivityTime) {
        this.firstActivityTime = firstActivityTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getEmptyCheckCount() {
        return emptyCheckCount;
    }

    public void setEmptyCheckCount(Integer emptyCheckCount) {
        this.emptyCheckCount = emptyCheckCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Boolean getCallbackSent() {
        return callbackSent;
    }

    public void setCallbackSent(Boolean callbackSent) {
        this.callbackSent = callbackSent;
    }

    public LocalDateTime getCallbackSentTime() {
        return callbackSentTime;
    }

    public void setCallbackSentTime(LocalDateTime callbackSentTime) {
        this.callbackSentTime = callbackSentTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "FlowExecutionHistory{" +
                "id=" + id +
                ", processGroupId='" + processGroupId + '\'' +
                ", processGroupName='" + processGroupName + '\'' +
                ", monitoringSessionId='" + monitoringSessionId + '\'' +
                ", status='" + status + '\'' +
                ", completed=" + completed +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", durationMs=" + durationMs +
                '}';
    }
}

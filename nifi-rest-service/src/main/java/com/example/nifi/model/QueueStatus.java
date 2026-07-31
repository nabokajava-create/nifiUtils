package com.example.nifi.model;

/**
 * Модель для представления статуса очереди
 */
public class QueueStatus {
    
    private String connectionId;
    private String sourceName;
    private String destinationName;
    private int flowFileCount;
    private long queueSize;
    private long maxQueueSize;
    private boolean isFull;
    private boolean isEmpty;

    public QueueStatus() {}

    public QueueStatus(String connectionId, String sourceName, String destinationName, 
                       int flowFileCount, long queueSize, long maxQueueSize) {
        this.connectionId = connectionId;
        this.sourceName = sourceName;
        this.destinationName = destinationName;
        this.flowFileCount = flowFileCount;
        this.queueSize = queueSize;
        this.maxQueueSize = maxQueueSize;
        this.isFull = maxQueueSize > 0 && queueSize >= maxQueueSize;
        this.isEmpty = flowFileCount == 0 && queueSize == 0;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public int getFlowFileCount() {
        return flowFileCount;
    }

    public void setFlowFileCount(int flowFileCount) {
        this.flowFileCount = flowFileCount;
    }

    public long getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(long queueSize) {
        this.queueSize = queueSize;
    }

    public long getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(long maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public boolean isFull() {
        return isFull;
    }

    public void setFull(boolean full) {
        isFull = full;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void setEmpty(boolean empty) {
        isEmpty = empty;
    }

    @Override
    public String toString() {
        return "QueueStatus{" +
                "connectionId='" + connectionId + '\'' +
                ", sourceName='" + sourceName + '\'' +
                ", destinationName='" + destinationName + '\'' +
                ", flowFileCount=" + flowFileCount +
                ", queueSize=" + queueSize +
                ", maxQueueSize=" + maxQueueSize +
                '}';
    }
}

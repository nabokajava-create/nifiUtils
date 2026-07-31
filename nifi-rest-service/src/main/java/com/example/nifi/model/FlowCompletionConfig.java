package com.example.nifi.model;

/**
 * Конфигурация для мониторинга завершения потока
 */
public class FlowCompletionConfig {
    
    /**
     * Максимальное время ожидания завершения потока (мс)
     */
    private long maxWaitTimeMs = 3600000; // 1 час по умолчанию
    
    /**
     * Интервал между проверками статуса (мс)
     */
    private long checkIntervalMs = 5000; // 5 секунд по умолчанию
    
    /**
     * Порог количества файлов в очереди для считания её пустой
     */
    private int emptyQueueThreshold = 0;
    
    /**
     * Порог размера очереди в байтах для считания её пустой
     */
    private long emptyQueueSizeThreshold = 0L;
    
    /**
     * Количество последовательных проверок с пустыми очередями для подтверждения завершения
     */
    private int consecutiveEmptyChecksRequired = 3;
    
    /**
     * Игнорировать очереди с определёнными именами (regex)
     */
    private String[] ignoredQueuePatterns = new String[0];
    
    /**
     * Учитывать только активные процессоры при проверке
     */
    private boolean considerOnlyActiveProcessors = true;

    public FlowCompletionConfig() {}

    public long getMaxWaitTimeMs() {
        return maxWaitTimeMs;
    }

    public void setMaxWaitTimeMs(long maxWaitTimeMs) {
        this.maxWaitTimeMs = maxWaitTimeMs;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public int getEmptyQueueThreshold() {
        return emptyQueueThreshold;
    }

    public void setEmptyQueueThreshold(int emptyQueueThreshold) {
        this.emptyQueueThreshold = emptyQueueThreshold;
    }

    public long getEmptyQueueSizeThreshold() {
        return emptyQueueSizeThreshold;
    }

    public void setEmptyQueueSizeThreshold(long emptyQueueSizeThreshold) {
        this.emptyQueueSizeThreshold = emptyQueueSizeThreshold;
    }

    public int getConsecutiveEmptyChecksRequired() {
        return consecutiveEmptyChecksRequired;
    }

    public void setConsecutiveEmptyChecksRequired(int consecutiveEmptyChecksRequired) {
        this.consecutiveEmptyChecksRequired = consecutiveEmptyChecksRequired;
    }

    public String[] getIgnoredQueuePatterns() {
        return ignoredQueuePatterns;
    }

    public void setIgnoredQueuePatterns(String[] ignoredQueuePatterns) {
        this.ignoredQueuePatterns = ignoredQueuePatterns;
    }

    public boolean isConsiderOnlyActiveProcessors() {
        return considerOnlyActiveProcessors;
    }

    public void setConsiderOnlyActiveProcessors(boolean considerOnlyActiveProcessors) {
        this.considerOnlyActiveProcessors = considerOnlyActiveProcessors;
    }
}

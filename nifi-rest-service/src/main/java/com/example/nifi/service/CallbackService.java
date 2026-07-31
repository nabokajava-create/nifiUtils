package com.example.nifi.service;

import com.example.nifi.config.NifiApiProperties;
import com.example.nifi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для отправки результатов мониторинга обратно в NiFi через callback URL.
 */
@Service
public class CallbackService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackService.class);

    private final RestTemplate restTemplate;

    public CallbackService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Отправить результат мониторинга на callback URL в NiFi
     */
    public boolean sendCallback(String callbackUrl, FlowMonitoringResult result) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            logger.debug("No callback URL provided, skipping callback");
            return false;
        }

        try {
            logger.info("Sending callback to {}: status={}, correlationId={}", 
                       callbackUrl, result.getStatus(), result.getCorrelationId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<FlowMonitoringResult> request = new HttpEntity<>(result, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(callbackUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Callback sent successfully to {}", callbackUrl);
                return true;
            } else {
                logger.warn("Callback returned non-success status: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send callback to {}: {}", callbackUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Отправить результат мониторинга с повторными попытками
     */
    public boolean sendCallbackWithRetry(String callbackUrl, FlowMonitoringResult result, int maxRetries) {
        int attempts = 0;
        
        while (attempts < maxRetries) {
            if (sendCallback(callbackUrl, result)) {
                return true;
            }
            
            attempts++;
            if (attempts < maxRetries) {
                try {
                    long delay = 1000L * attempts; // Экспоненциальная задержка: 1s, 2s, 3s...
                    logger.debug("Retry {} after {} ms", attempts, delay);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.error("All {} callback attempts failed for {}", maxRetries, callbackUrl);
        return false;
    }
}

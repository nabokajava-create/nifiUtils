package com.example.nifi.service;

import com.example.nifi.config.NifiApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class NifiService {

    private static final Logger logger = LoggerFactory.getLogger(NifiService.class);

    private final RestTemplate restTemplate;
    private final NifiApiProperties nifiApiProperties;

    public NifiService(RestTemplate restTemplate, NifiApiProperties nifiApiProperties) {
        this.restTemplate = restTemplate;
        this.nifiApiProperties = nifiApiProperties;
    }

    /**
     * Get NiFi system status
     */
    public Map<String, Object> getSystemStatus() {
        String url = nifiApiProperties.getBaseUrl() + "/flow/about";
        logger.debug("Calling NiFi API: {}", url);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createEntity(),
                Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error getting system status: {}", e.getMessage());
            throw new RuntimeException("Failed to get NiFi system status", e);
        }
    }

    /**
     * Get all process groups
     */
    public List<Map<String, Object>> getProcessGroups() {
        String url = nifiApiProperties.getBaseUrl() + "/process-groups/root";
        logger.debug("Calling NiFi API: {}", url);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createEntity(),
                Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("component")) {
                Map<String, Object> component = (Map<String, Object>) body.get("component");
                List<Map<String, Object>> childGroups = (List<Map<String, Object>>) component.get("childGroups");
                return childGroups != null ? childGroups : new ArrayList<>();
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting process groups: {}", e.getMessage());
            throw new RuntimeException("Failed to get process groups", e);
        }
    }

    /**
     * Get processors in a process group
     */
    public List<Map<String, Object>> getProcessors(String processGroupId) {
        String url = nifiApiProperties.getBaseUrl() + "/process-groups/" + processGroupId + "/processors";
        logger.debug("Calling NiFi API: {}", url);
        
        try {
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
        } catch (Exception e) {
            logger.error("Error getting processors: {}", e.getMessage());
            throw new RuntimeException("Failed to get processors", e);
        }
    }

    /**
     * Start a processor
     */
    public Map<String, Object> startProcessor(String processorId) {
        return changeProcessorState(processorId, "RUNNING");
    }

    /**
     * Stop a processor
     */
    public Map<String, Object> stopProcessor(String processorId) {
        return changeProcessorState(processorId, "STOPPED");
    }

    /**
     * Change processor state
     */
    private Map<String, Object> changeProcessorState(String processorId, String state) {
        String url = nifiApiProperties.getBaseUrl() + "/processors/" + processorId + "/run-status";
        logger.debug("Changing processor {} state to {}", processorId, state);
        
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("state", state);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                Map.class
            );
            
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error changing processor state: {}", e.getMessage());
            throw new RuntimeException("Failed to change processor state", e);
        }
    }

    /**
     * Get connection status
     */
    public List<Map<String, Object>> getConnections(String processGroupId) {
        String url = nifiApiProperties.getBaseUrl() + "/process-groups/" + processGroupId + "/connections";
        logger.debug("Calling NiFi API: {}", url);
        
        try {
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
        } catch (Exception e) {
            logger.error("Error getting connections: {}", e.getMessage());
            throw new RuntimeException("Failed to get connections", e);
        }
    }

    /**
     * Get flow statistics
     */
    public Map<String, Object> getFlowStatistics() {
        String url = nifiApiProperties.getBaseUrl() + "/flow/status";
        logger.debug("Calling NiFi API: {}", url);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createEntity(),
                Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error getting flow statistics: {}", e.getMessage());
            throw new RuntimeException("Failed to get flow statistics", e);
        }
    }

    /**
     * Create HTTP entity with headers
     */
    private HttpEntity<Void> createEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }
}

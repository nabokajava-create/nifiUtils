package com.example.nifi.controller;

import com.example.nifi.service.NifiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nifi")
@CrossOrigin(origins = "*")
public class NifiController {

    private static final Logger logger = LoggerFactory.getLogger(NifiController.class);

    private final NifiService nifiService;

    public NifiController(NifiService nifiService) {
        this.nifiService = nifiService;
    }

    /**
     * Get NiFi system information
     * GET /api/nifi/system
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        logger.info("Getting NiFi system status");
        try {
            Map<String, Object> status = nifiService.getSystemStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting system status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all process groups
     * GET /api/nifi/process-groups
     */
    @GetMapping("/process-groups")
    public ResponseEntity<List<Map<String, Object>>> getProcessGroups() {
        logger.info("Getting process groups");
        try {
            List<Map<String, Object>> groups = nifiService.getProcessGroups();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            logger.error("Error getting process groups", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get processors in a process group
     * GET /api/nifi/process-groups/{id}/processors
     */
    @GetMapping("/process-groups/{id}/processors")
    public ResponseEntity<List<Map<String, Object>>> getProcessors(@PathVariable String id) {
        logger.info("Getting processors for process group: {}", id);
        try {
            List<Map<String, Object>> processors = nifiService.getProcessors(id);
            return ResponseEntity.ok(processors);
        } catch (Exception e) {
            logger.error("Error getting processors", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get connections in a process group
     * GET /api/nifi/process-groups/{id}/connections
     */
    @GetMapping("/process-groups/{id}/connections")
    public ResponseEntity<List<Map<String, Object>>> getConnections(@PathVariable String id) {
        logger.info("Getting connections for process group: {}", id);
        try {
            List<Map<String, Object>> connections = nifiService.getConnections(id);
            return ResponseEntity.ok(connections);
        } catch (Exception e) {
            logger.error("Error getting connections", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Start a processor
     * PUT /api/nifi/processors/{id}/start
     */
    @PutMapping("/processors/{id}/start")
    public ResponseEntity<Map<String, Object>> startProcessor(@PathVariable String id) {
        logger.info("Starting processor: {}", id);
        try {
            Map<String, Object> result = nifiService.startProcessor(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error starting processor", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Stop a processor
     * PUT /api/nifi/processors/{id}/stop
     */
    @PutMapping("/processors/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopProcessor(@PathVariable String id) {
        logger.info("Stopping processor: {}", id);
        try {
            Map<String, Object> result = nifiService.stopProcessor(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error stopping processor", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get flow statistics
     * GET /api/nifi/flow/status
     */
    @GetMapping("/flow/status")
    public ResponseEntity<Map<String, Object>> getFlowStatistics() {
        logger.info("Getting flow statistics");
        try {
            Map<String, Object> stats = nifiService.getFlowStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting flow statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/nifi/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        logger.debug("Health check");
        Map<String, String> health = Map.of("status", "UP", "service", "nifi-rest-service");
        return ResponseEntity.ok(health);
    }
}

package com.lowcode.controller;

import com.lowcode.dto.CheckpointActivityDTO;
import com.lowcode.service.CheckpointService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

/**
 * REST Controller for Checkpoint operations (Undo/Redo/Activities)
 */
@RestController
@RequestMapping("/api/apps/{appId}/checkpoints")
@Slf4j
public class CheckpointController {

    private final CheckpointService checkpointService;

    public CheckpointController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    /**
     * Set active user for checkpoint tracking
     */
    @PostMapping("/user")
    public ResponseEntity<Void> setActiveUser(@PathVariable String appId,
                                              @RequestBody SetUserRequest request) {
        checkpointService.setActiveUser(appId, request.getUserId());
        return ResponseEntity.ok().build();
    }

    /**
     * Perform undo operation
     */
    @PostMapping("/undo")
    public ResponseEntity<Void> undo(@PathVariable String appId,
                                     @RequestParam String userId) {
        try {
            log.info("Undo requested for app: {} user: {}", appId, userId);
            checkpointService.undo(appId, userId);
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            log.error("Undo failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Perform redo operation
     */
    @PostMapping("/redo")
    public ResponseEntity<Void> redo(@PathVariable String appId,
                                     @RequestParam String userId) {
        try {
            log.info("Redo requested for app: {} user: {}", appId, userId);
            checkpointService.redo(appId, userId);
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            log.error("Redo failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get checkpoint activities (for UI timeline)
     */
    @GetMapping("/activities")
    public ResponseEntity<List<CheckpointActivityDTO>> getActivities(@PathVariable String appId,
                                                                      @RequestParam String userId) {
        try {
            List<CheckpointActivityDTO> activities = checkpointService.getActivities(appId, userId);
            return ResponseEntity.ok(activities);
        } catch (SQLException e) {
            log.error("Failed to get activities", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Revert to a specific checkpoint
     */
    @PostMapping("/revert/{checkpointId}")
    public ResponseEntity<Void> revertToCheckpoint(@PathVariable String appId,
                                                    @PathVariable Long checkpointId,
                                                    @RequestParam String userId) {
        try {
            log.info("Revert to checkpoint requested: {} for app: {} user: {}",
                    checkpointId, appId, userId);
            checkpointService.revertToCheckpoint(appId, userId, checkpointId);
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            log.error("Revert failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class SetUserRequest {
        private String userId;
    }
}

package com.lowcode.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Checkpoint Activity DTO - represents a checkpoint with its changes
 * Used for displaying activity timeline in the UI
 */
@Data
public class CheckpointActivityDTO {
    private Long id;
    private String name;
    private String gtid;
    private LocalDateTime createdAt;
    private List<ChangeDetail> changes;
    private Boolean isCurrent;  // Is this the current checkpoint position?

    @Data
    public static class ChangeDetail {
        private String eventType;  // INSERT, UPDATE, DELETE
        private String tableName;
        private String description;  // Human-readable description of change
        private Object beforeData;
        private Object afterData;
    }
}

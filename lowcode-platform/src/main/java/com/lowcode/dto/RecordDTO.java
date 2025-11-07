package com.lowcode.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Record DTO - represents a form record/entry
 */
@Data
public class RecordDTO {
    private String id;
    private String formId;
    private Map<String, Object> data;  // Field name -> value mapping
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

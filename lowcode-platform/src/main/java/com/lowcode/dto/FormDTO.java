package com.lowcode.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Form DTO - represents a form in an app
 */
@Data
public class FormDTO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Transient fields (not in DB)
    private List<FieldDTO> fields;
    private ReportDTO defaultReport;
}

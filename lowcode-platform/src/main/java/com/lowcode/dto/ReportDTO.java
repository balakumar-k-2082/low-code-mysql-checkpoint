package com.lowcode.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Report DTO - represents a report configuration
 */
@Data
public class ReportDTO {
    private String id;
    private String formId;
    private String name;
    private String description;
    private Boolean isDefault;
    private List<SortConfig> sortConfig;
    private List<FilterConfig> filterConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class SortConfig {
        private String field;
        private String direction;  // asc, desc
    }

    @Data
    public static class FilterConfig {
        private String field;
        private String operator;  // equals, contains, startsWith, endsWith, greaterThan, lessThan
        private String value;
    }
}

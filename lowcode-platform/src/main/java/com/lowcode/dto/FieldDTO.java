package com.lowcode.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Field DTO - represents a form field
 */
@Data
public class FieldDTO {
    private String id;
    private String formId;
    private String name;
    private String label;
    private String fieldType;  // text, email, phone, textarea, checkbox, dropdown, etc.
    private Boolean isRequired;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Field properties (placeholder, options, validation, etc.)
    private Map<String, String> properties;

    // Field type constants
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_EMAIL = "email";
    public static final String TYPE_PHONE = "phone";
    public static final String TYPE_TEXTAREA = "textarea";
    public static final String TYPE_CHECKBOX = "checkbox";
    public static final String TYPE_DROPDOWN = "dropdown";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_DATE = "date";
}

package com.lowcode.service;

import com.lowcode.dto.FieldDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Service for managing form fields
 */
@Service
@Slf4j
public class FieldService {

    private final JdbcTemplate jdbcTemplate;

    public FieldService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get all fields for a form
     */
    public List<FieldDTO> getFieldsByForm(String schemaName, String formId) {
        String sql = String.format(
            "SELECT * FROM %s.fields WHERE form_id = ? ORDER BY display_order, name",
            schemaName
        );

        List<FieldDTO> fields = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToField(rs), formId);

        // Load properties for each field
        for (FieldDTO field : fields) {
            field.setProperties(getFieldProperties(schemaName, field.getId()));
        }

        return fields;
    }

    /**
     * Get field by ID
     */
    public FieldDTO getFieldById(String schemaName, String fieldId) {
        String sql = String.format(
            "SELECT * FROM %s.fields WHERE id = ?", schemaName
        );

        List<FieldDTO> fields = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToField(rs), fieldId);

        if (fields.isEmpty()) {
            throw new RuntimeException("Field not found: " + fieldId);
        }

        FieldDTO field = fields.get(0);
        field.setProperties(getFieldProperties(schemaName, fieldId));

        return field;
    }

    /**
     * Create a new field
     */
    @Transactional
    public FieldDTO createField(String schemaName, FieldDTO field) {
        log.info("Creating field in schema: {}", schemaName);

        String id = UUID.randomUUID().toString();

        String sql = String.format(
            "INSERT INTO %s.fields (id, form_id, name, label, field_type, is_required, display_order) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            schemaName
        );

        jdbcTemplate.update(sql,
            id,
            field.getFormId(),
            field.getName(),
            field.getLabel(),
            field.getFieldType(),
            field.getIsRequired() != null ? field.getIsRequired() : false,
            field.getDisplayOrder() != null ? field.getDisplayOrder() : 0
        );

        // Save properties
        if (field.getProperties() != null && !field.getProperties().isEmpty()) {
            saveFieldProperties(schemaName, id, field.getProperties());
        }

        return getFieldById(schemaName, id);
    }

    /**
     * Update field
     */
    @Transactional
    public FieldDTO updateField(String schemaName, String fieldId, FieldDTO field) {
        String sql = String.format(
            "UPDATE %s.fields SET name = ?, label = ?, field_type = ?, is_required = ?, display_order = ? " +
            "WHERE id = ?",
            schemaName
        );

        int updated = jdbcTemplate.update(sql,
            field.getName(),
            field.getLabel(),
            field.getFieldType(),
            field.getIsRequired(),
            field.getDisplayOrder(),
            fieldId
        );

        if (updated == 0) {
            throw new RuntimeException("Field not found: " + fieldId);
        }

        // Update properties
        if (field.getProperties() != null) {
            // Delete existing properties
            deleteFieldProperties(schemaName, fieldId);
            // Save new properties
            saveFieldProperties(schemaName, fieldId, field.getProperties());
        }

        return getFieldById(schemaName, fieldId);
    }

    /**
     * Delete field
     */
    @Transactional
    public void deleteField(String schemaName, String fieldId) {
        String sql = String.format("DELETE FROM %s.fields WHERE id = ?", schemaName);
        jdbcTemplate.update(sql, fieldId);
    }

    /**
     * Get field properties
     */
    private Map<String, String> getFieldProperties(String schemaName, String fieldId) {
        String sql = String.format(
            "SELECT property_key, property_value FROM %s.field_properties WHERE field_id = ?",
            schemaName
        );

        Map<String, String> properties = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            properties.put(rs.getString("property_key"), rs.getString("property_value"));
        }, fieldId);

        return properties;
    }

    /**
     * Save field properties
     */
    private void saveFieldProperties(String schemaName, String fieldId, Map<String, String> properties) {
        String sql = String.format(
            "INSERT INTO %s.field_properties (id, field_id, property_key, property_value) VALUES (?, ?, ?, ?)",
            schemaName
        );

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                fieldId,
                entry.getKey(),
                entry.getValue()
            );
        }
    }

    /**
     * Delete field properties
     */
    private void deleteFieldProperties(String schemaName, String fieldId) {
        String sql = String.format("DELETE FROM %s.field_properties WHERE field_id = ?", schemaName);
        jdbcTemplate.update(sql, fieldId);
    }

    /**
     * Map ResultSet row to FieldDTO
     */
    private FieldDTO mapRowToField(ResultSet rs) throws SQLException {
        FieldDTO field = new FieldDTO();
        field.setId(rs.getString("id"));
        field.setFormId(rs.getString("form_id"));
        field.setName(rs.getString("name"));
        field.setLabel(rs.getString("label"));
        field.setFieldType(rs.getString("field_type"));
        field.setIsRequired(rs.getBoolean("is_required"));
        field.setDisplayOrder(rs.getInt("display_order"));
        field.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        field.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return field;
    }
}

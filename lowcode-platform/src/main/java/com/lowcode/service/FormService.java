package com.lowcode.service;

import com.google.gson.Gson;
import com.lowcode.dto.FieldDTO;
import com.lowcode.dto.FormDTO;
import com.lowcode.dto.ReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Service for managing forms within an app
 */
@Service
@Slf4j
public class FormService {

    private final JdbcTemplate jdbcTemplate;
    private final FieldService fieldService;
    private final ReportService reportService;
    private final Gson gson = new Gson();

    public FormService(JdbcTemplate jdbcTemplate,
                      FieldService fieldService,
                      ReportService reportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldService = fieldService;
        this.reportService = reportService;
    }

    /**
     * Get all forms for an app
     */
    public List<FormDTO> getFormsByApp(String schemaName) {
        String sql = String.format(
            "SELECT * FROM %s.forms ORDER BY display_order, name", schemaName
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToForm(rs));
    }

    /**
     * Get form by ID
     */
    public FormDTO getFormById(String schemaName, String formId) {
        String sql = String.format(
            "SELECT * FROM %s.forms WHERE id = ?", schemaName
        );

        List<FormDTO> forms = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToForm(rs), formId);

        if (forms.isEmpty()) {
            throw new RuntimeException("Form not found: " + formId);
        }

        FormDTO form = forms.get(0);

        // Load fields
        form.setFields(fieldService.getFieldsByForm(schemaName, formId));

        // Load default report
        ReportDTO defaultReport = reportService.getDefaultReportForForm(schemaName, formId);
        form.setDefaultReport(defaultReport);

        return form;
    }

    /**
     * Create a new form
     */
    @Transactional
    public FormDTO createForm(String schemaName, FormDTO form) {
        log.info("Creating form in schema: {}", schemaName);

        String id = UUID.randomUUID().toString();

        String sql = String.format(
            "INSERT INTO %s.forms (id, name, description, icon, display_order) VALUES (?, ?, ?, ?, ?)",
            schemaName
        );

        jdbcTemplate.update(sql,
            id,
            form.getName(),
            form.getDescription(),
            form.getIcon(),
            form.getDisplayOrder() != null ? form.getDisplayOrder() : 0
        );

        // Create default report
        reportService.createDefaultReport(schemaName, id, form.getName());

        return getFormById(schemaName, id);
    }

    /**
     * Update form
     */
    @Transactional
    public FormDTO updateForm(String schemaName, String formId, FormDTO form) {
        String sql = String.format(
            "UPDATE %s.forms SET name = ?, description = ?, icon = ?, display_order = ? WHERE id = ?",
            schemaName
        );

        int updated = jdbcTemplate.update(sql,
            form.getName(),
            form.getDescription(),
            form.getIcon(),
            form.getDisplayOrder(),
            formId
        );

        if (updated == 0) {
            throw new RuntimeException("Form not found: " + formId);
        }

        return getFormById(schemaName, formId);
    }

    /**
     * Delete form
     */
    @Transactional
    public void deleteForm(String schemaName, String formId) {
        String sql = String.format("DELETE FROM %s.forms WHERE id = ?", schemaName);
        jdbcTemplate.update(sql, formId);
    }

    /**
     * Map ResultSet row to FormDTO
     */
    private FormDTO mapRowToForm(ResultSet rs) throws SQLException {
        FormDTO form = new FormDTO();
        form.setId(rs.getString("id"));
        form.setName(rs.getString("name"));
        form.setDescription(rs.getString("description"));
        form.setIcon(rs.getString("icon"));
        form.setDisplayOrder(rs.getInt("display_order"));
        form.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        form.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return form;
    }
}

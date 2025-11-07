package com.lowcode.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lowcode.dto.ReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing reports
 */
@Service
@Slf4j
public class ReportService {

    private final JdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get default report for a form
     */
    public ReportDTO getDefaultReportForForm(String schemaName, String formId) {
        String sql = String.format(
            "SELECT * FROM %s.reports WHERE form_id = ? AND is_default = TRUE LIMIT 1",
            schemaName
        );

        List<ReportDTO> reports = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToReport(rs), formId);

        return reports.isEmpty() ? null : reports.get(0);
    }

    /**
     * Get all reports for a form
     */
    public List<ReportDTO> getReportsByForm(String schemaName, String formId) {
        String sql = String.format(
            "SELECT * FROM %s.reports WHERE form_id = ? ORDER BY is_default DESC, name",
            schemaName
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToReport(rs), formId);
    }

    /**
     * Get report by ID
     */
    public ReportDTO getReportById(String schemaName, String reportId) {
        String sql = String.format(
            "SELECT * FROM %s.reports WHERE id = ?", schemaName
        );

        List<ReportDTO> reports = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToReport(rs), reportId);

        if (reports.isEmpty()) {
            throw new RuntimeException("Report not found: " + reportId);
        }

        return reports.get(0);
    }

    /**
     * Create default report for a form
     */
    @Transactional
    public ReportDTO createDefaultReport(String schemaName, String formId, String formName) {
        String id = UUID.randomUUID().toString();

        String sql = String.format(
            "INSERT INTO %s.reports (id, form_id, name, description, is_default, sort_config, filter_config) " +
            "VALUES (?, ?, ?, ?, TRUE, NULL, NULL)",
            schemaName
        );

        jdbcTemplate.update(sql,
            id,
            formId,
            formName + " Report",
            "Default report for " + formName
        );

        return getReportById(schemaName, id);
    }

    /**
     * Create a new report
     */
    @Transactional
    public ReportDTO createReport(String schemaName, ReportDTO report) {
        String id = UUID.randomUUID().toString();

        String sortConfigJson = report.getSortConfig() != null ?
            gson.toJson(report.getSortConfig()) : null;
        String filterConfigJson = report.getFilterConfig() != null ?
            gson.toJson(report.getFilterConfig()) : null;

        String sql = String.format(
            "INSERT INTO %s.reports (id, form_id, name, description, is_default, sort_config, filter_config) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            schemaName
        );

        jdbcTemplate.update(sql,
            id,
            report.getFormId(),
            report.getName(),
            report.getDescription(),
            report.getIsDefault() != null ? report.getIsDefault() : false,
            sortConfigJson,
            filterConfigJson
        );

        return getReportById(schemaName, id);
    }

    /**
     * Update report
     */
    @Transactional
    public ReportDTO updateReport(String schemaName, String reportId, ReportDTO report) {
        String sortConfigJson = report.getSortConfig() != null ?
            gson.toJson(report.getSortConfig()) : null;
        String filterConfigJson = report.getFilterConfig() != null ?
            gson.toJson(report.getFilterConfig()) : null;

        String sql = String.format(
            "UPDATE %s.reports SET name = ?, description = ?, is_default = ?, " +
            "sort_config = ?, filter_config = ? WHERE id = ?",
            schemaName
        );

        int updated = jdbcTemplate.update(sql,
            report.getName(),
            report.getDescription(),
            report.getIsDefault(),
            sortConfigJson,
            filterConfigJson,
            reportId
        );

        if (updated == 0) {
            throw new RuntimeException("Report not found: " + reportId);
        }

        return getReportById(schemaName, reportId);
    }

    /**
     * Delete report
     */
    @Transactional
    public void deleteReport(String schemaName, String reportId) {
        // Don't allow deleting default reports
        ReportDTO report = getReportById(schemaName, reportId);
        if (report.getIsDefault()) {
            throw new RuntimeException("Cannot delete default report");
        }

        String sql = String.format("DELETE FROM %s.reports WHERE id = ?", schemaName);
        jdbcTemplate.update(sql, reportId);
    }

    /**
     * Map ResultSet row to ReportDTO
     */
    private ReportDTO mapRowToReport(ResultSet rs) throws SQLException {
        ReportDTO report = new ReportDTO();
        report.setId(rs.getString("id"));
        report.setFormId(rs.getString("form_id"));
        report.setName(rs.getString("name"));
        report.setDescription(rs.getString("description"));
        report.setIsDefault(rs.getBoolean("is_default"));

        // Parse JSON configs
        String sortConfigJson = rs.getString("sort_config");
        if (sortConfigJson != null) {
            report.setSortConfig(gson.fromJson(sortConfigJson,
                new TypeToken<List<ReportDTO.SortConfig>>(){}.getType()));
        }

        String filterConfigJson = rs.getString("filter_config");
        if (filterConfigJson != null) {
            report.setFilterConfig(gson.fromJson(filterConfigJson,
                new TypeToken<List<ReportDTO.FilterConfig>>(){}.getType()));
        }

        report.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        report.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        return report;
    }
}

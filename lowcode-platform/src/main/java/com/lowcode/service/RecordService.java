package com.lowcode.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lowcode.dto.RecordDTO;
import com.lowcode.dto.ReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing form records (data)
 */
@Service
@Slf4j
public class RecordService {

    private final JdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public RecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get all records for a form
     */
    public List<RecordDTO> getRecordsByForm(String schemaName, String formId) {
        String sql = String.format(
            "SELECT * FROM %s.records WHERE form_id = ? ORDER BY created_at DESC",
            schemaName
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToRecord(rs), formId);
    }

    /**
     * Get records with report filters and sorting
     */
    public List<RecordDTO> getRecordsByReport(String schemaName, String formId, ReportDTO report) {
        StringBuilder sql = new StringBuilder();
        sql.append(String.format("SELECT * FROM %s.records WHERE form_id = ?", schemaName));

        List<Object> params = new ArrayList<>();
        params.add(formId);

        // Apply filters (basic implementation - can be enhanced)
        if (report.getFilterConfig() != null && !report.getFilterConfig().isEmpty()) {
            // For JSON filtering, we'd need to use JSON_EXTRACT
            // This is a simplified version
            log.debug("Applying {} filters", report.getFilterConfig().size());
        }

        // Apply sorting
        if (report.getSortConfig() != null && !report.getSortConfig().isEmpty()) {
            sql.append(" ORDER BY ");
            List<String> sortClauses = new ArrayList<>();
            for (ReportDTO.SortConfig sort : report.getSortConfig()) {
                // Use JSON_EXTRACT for sorting by field values
                String clause = String.format(
                    "JSON_UNQUOTE(JSON_EXTRACT(data, '$.%s')) %s",
                    sort.getField(), sort.getDirection().toUpperCase()
                );
                sortClauses.add(clause);
            }
            sql.append(String.join(", ", sortClauses));
        } else {
            sql.append(" ORDER BY created_at DESC");
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapRowToRecord(rs),
            params.toArray());
    }

    /**
     * Get record by ID
     */
    public RecordDTO getRecordById(String schemaName, String recordId) {
        String sql = String.format(
            "SELECT * FROM %s.records WHERE id = ?", schemaName
        );

        List<RecordDTO> records = jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToRecord(rs), recordId);

        if (records.isEmpty()) {
            throw new RuntimeException("Record not found: " + recordId);
        }

        return records.get(0);
    }

    /**
     * Create a new record
     */
    @Transactional
    public RecordDTO createRecord(String schemaName, RecordDTO record) {
        log.info("Creating record in schema: {}", schemaName);

        String id = UUID.randomUUID().toString();
        String dataJson = gson.toJson(record.getData());

        String sql = String.format(
            "INSERT INTO %s.records (id, form_id, data, created_by) VALUES (?, ?, ?, ?)",
            schemaName
        );

        jdbcTemplate.update(sql,
            id,
            record.getFormId(),
            dataJson,
            record.getCreatedBy()
        );

        return getRecordById(schemaName, id);
    }

    /**
     * Update record
     */
    @Transactional
    public RecordDTO updateRecord(String schemaName, String recordId, RecordDTO record) {
        String dataJson = gson.toJson(record.getData());

        String sql = String.format(
            "UPDATE %s.records SET data = ? WHERE id = ?",
            schemaName
        );

        int updated = jdbcTemplate.update(sql, dataJson, recordId);

        if (updated == 0) {
            throw new RuntimeException("Record not found: " + recordId);
        }

        return getRecordById(schemaName, recordId);
    }

    /**
     * Delete record
     */
    @Transactional
    public void deleteRecord(String schemaName, String recordId) {
        String sql = String.format("DELETE FROM %s.records WHERE id = ?", schemaName);
        jdbcTemplate.update(sql, recordId);
    }

    /**
     * Map ResultSet row to RecordDTO
     */
    private RecordDTO mapRowToRecord(ResultSet rs) throws SQLException {
        RecordDTO record = new RecordDTO();
        record.setId(rs.getString("id"));
        record.setFormId(rs.getString("form_id"));

        // Parse JSON data
        String dataJson = rs.getString("data");
        if (dataJson != null) {
            Map<String, Object> data = gson.fromJson(dataJson,
                new TypeToken<Map<String, Object>>(){}.getType());
            record.setData(data);
        }

        record.setCreatedBy(rs.getString("created_by"));
        record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        record.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        return record;
    }
}

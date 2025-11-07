package com.lowcode.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Service for managing per-app databases
 */
@Service
@Slf4j
public class DatabaseService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Create a new app database with required tables
     */
    public void createAppDatabase(String schemaName) {
        log.info("Creating app database: {}", schemaName);

        try {
            // Create the database
            jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS " + schemaName);

            // Create forms table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + schemaName + ".forms (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  name VARCHAR(255) NOT NULL," +
                "  description TEXT," +
                "  icon VARCHAR(50)," +
                "  display_order INT DEFAULT 0," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  INDEX idx_display_order (display_order)" +
                ") ENGINE=InnoDB"
            );

            // Create fields table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + schemaName + ".fields (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  form_id VARCHAR(36) NOT NULL," +
                "  name VARCHAR(255) NOT NULL," +
                "  label VARCHAR(255) NOT NULL," +
                "  field_type VARCHAR(50) NOT NULL," +
                "  is_required BOOLEAN DEFAULT FALSE," +
                "  display_order INT DEFAULT 0," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (form_id) REFERENCES " + schemaName + ".forms(id) ON DELETE CASCADE," +
                "  INDEX idx_form_display (form_id, display_order)" +
                ") ENGINE=InnoDB"
            );

            // Create field_properties table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + schemaName + ".field_properties (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  field_id VARCHAR(36) NOT NULL," +
                "  property_key VARCHAR(100) NOT NULL," +
                "  property_value TEXT," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (field_id) REFERENCES " + schemaName + ".fields(id) ON DELETE CASCADE," +
                "  UNIQUE KEY uk_field_property (field_id, property_key)," +
                "  INDEX idx_field_id (field_id)" +
                ") ENGINE=InnoDB"
            );

            // Create reports table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + schemaName + ".reports (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  form_id VARCHAR(36) NOT NULL," +
                "  name VARCHAR(255) NOT NULL," +
                "  description TEXT," +
                "  is_default BOOLEAN DEFAULT FALSE," +
                "  sort_config JSON," +
                "  filter_config JSON," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (form_id) REFERENCES " + schemaName + ".forms(id) ON DELETE CASCADE," +
                "  INDEX idx_form_id (form_id)," +
                "  INDEX idx_default (form_id, is_default)" +
                ") ENGINE=InnoDB"
            );

            // Create records table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + schemaName + ".records (" +
                "  id VARCHAR(36) PRIMARY KEY," +
                "  form_id VARCHAR(36) NOT NULL," +
                "  data JSON NOT NULL," +
                "  created_by VARCHAR(255)," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (form_id) REFERENCES " + schemaName + ".forms(id) ON DELETE CASCADE," +
                "  INDEX idx_form_id (form_id)," +
                "  INDEX idx_created_at (created_at)" +
                ") ENGINE=InnoDB"
            );

            log.info("Successfully created app database: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to create app database: {}", schemaName, e);
            throw new RuntimeException("Failed to create app database", e);
        }
    }

    /**
     * Delete an app database
     */
    public void deleteAppDatabase(String schemaName) {
        log.warn("Deleting app database: {}", schemaName);

        try {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + schemaName);
            log.info("Successfully deleted app database: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to delete app database: {}", schemaName, e);
            throw new RuntimeException("Failed to delete app database", e);
        }
    }

    /**
     * Check if app database exists
     */
    public boolean databaseExists(String schemaName) {
        String sql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
        return !jdbcTemplate.queryForList(sql, String.class, schemaName).isEmpty();
    }
}

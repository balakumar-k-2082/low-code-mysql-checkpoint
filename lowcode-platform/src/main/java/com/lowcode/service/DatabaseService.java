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
            // Call the stored procedure to create app database
            jdbcTemplate.execute("CALL lowcode_system.create_app_database('" + schemaName + "')");
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

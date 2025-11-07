-- Low-Code Platform Database Schema
-- This schema uses one database per app for isolation (as per checkpoint system design)

-- =============================================================================
-- MAIN DATABASE: lowcode_system (metadata for all apps)
-- =============================================================================

CREATE DATABASE IF NOT EXISTS lowcode_system;
USE lowcode_system;

-- Apps table - stores all applications
CREATE TABLE IF NOT EXISTS apps (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    schema_name VARCHAR(64) NOT NULL UNIQUE,  -- Each app gets its own database
    icon VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- =============================================================================
-- PER-APP DATABASE SCHEMA (template for each app_xxx database)
-- =============================================================================
-- Each app gets its own database named: app_{app_id}
-- The following tables are created in each app database:

-- Forms table - stores forms within the app
-- CREATE TABLE forms (
--     id VARCHAR(36) PRIMARY KEY,
--     name VARCHAR(255) NOT NULL,
--     description TEXT,
--     icon VARCHAR(50),
--     display_order INT DEFAULT 0,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     INDEX idx_display_order (display_order)
-- ) ENGINE=InnoDB;

-- Fields table - stores field definitions for forms
-- CREATE TABLE fields (
--     id VARCHAR(36) PRIMARY KEY,
--     form_id VARCHAR(36) NOT NULL,
--     name VARCHAR(255) NOT NULL,
--     label VARCHAR(255) NOT NULL,
--     field_type VARCHAR(50) NOT NULL,  -- text, email, phone, textarea, checkbox, dropdown, etc.
--     is_required BOOLEAN DEFAULT FALSE,
--     display_order INT DEFAULT 0,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
--     INDEX idx_form_display (form_id, display_order)
-- ) ENGINE=InnoDB;

-- Field properties - stores configuration for each field
-- CREATE TABLE field_properties (
--     id VARCHAR(36) PRIMARY KEY,
--     field_id VARCHAR(36) NOT NULL,
--     property_key VARCHAR(100) NOT NULL,
--     property_value TEXT,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     FOREIGN KEY (field_id) REFERENCES fields(id) ON DELETE CASCADE,
--     UNIQUE KEY uk_field_property (field_id, property_key),
--     INDEX idx_field_id (field_id)
-- ) ENGINE=InnoDB;

-- Reports table - stores report configurations
-- CREATE TABLE reports (
--     id VARCHAR(36) PRIMARY KEY,
--     form_id VARCHAR(36) NOT NULL,
--     name VARCHAR(255) NOT NULL,
--     description TEXT,
--     is_default BOOLEAN DEFAULT FALSE,
--     sort_config JSON,  -- [{field: 'name', direction: 'asc'}]
--     filter_config JSON,  -- [{field: 'status', operator: 'equals', value: 'active'}]
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
--     INDEX idx_form_id (form_id),
--     INDEX idx_default (form_id, is_default)
-- ) ENGINE=InnoDB;

-- Records table - stores actual form data as JSON
-- CREATE TABLE records (
--     id VARCHAR(36) PRIMARY KEY,
--     form_id VARCHAR(36) NOT NULL,
--     data JSON NOT NULL,  -- Stores all field values as JSON
--     created_by VARCHAR(255),
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
--     INDEX idx_form_id (form_id),
--     INDEX idx_created_at (created_at)
-- ) ENGINE=InnoDB;

-- =============================================================================
-- HELPER STORED PROCEDURE: Create app database
-- =============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS create_app_database$$

CREATE PROCEDURE create_app_database(IN app_schema_name VARCHAR(64))
BEGIN
    -- Create the app database
    SET @sql = CONCAT('CREATE DATABASE IF NOT EXISTS ', app_schema_name);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Use the app database
    SET @sql = CONCAT('USE ', app_schema_name);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create forms table
    SET @sql = CONCAT('
        CREATE TABLE IF NOT EXISTS ', app_schema_name, '.forms (
            id VARCHAR(36) PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            description TEXT,
            icon VARCHAR(50),
            display_order INT DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_display_order (display_order)
        ) ENGINE=InnoDB
    ');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create fields table
    SET @sql = CONCAT('
        CREATE TABLE IF NOT EXISTS ', app_schema_name, '.fields (
            id VARCHAR(36) PRIMARY KEY,
            form_id VARCHAR(36) NOT NULL,
            name VARCHAR(255) NOT NULL,
            label VARCHAR(255) NOT NULL,
            field_type VARCHAR(50) NOT NULL,
            is_required BOOLEAN DEFAULT FALSE,
            display_order INT DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
            INDEX idx_form_display (form_id, display_order)
        ) ENGINE=InnoDB
    ');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create field_properties table
    SET @sql = CONCAT('
        CREATE TABLE IF NOT EXISTS ', app_schema_name, '.field_properties (
            id VARCHAR(36) PRIMARY KEY,
            field_id VARCHAR(36) NOT NULL,
            property_key VARCHAR(100) NOT NULL,
            property_value TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (field_id) REFERENCES fields(id) ON DELETE CASCADE,
            UNIQUE KEY uk_field_property (field_id, property_key),
            INDEX idx_field_id (field_id)
        ) ENGINE=InnoDB
    ');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create reports table
    SET @sql = CONCAT('
        CREATE TABLE IF NOT EXISTS ', app_schema_name, '.reports (
            id VARCHAR(36) PRIMARY KEY,
            form_id VARCHAR(36) NOT NULL,
            name VARCHAR(255) NOT NULL,
            description TEXT,
            is_default BOOLEAN DEFAULT FALSE,
            sort_config JSON,
            filter_config JSON,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
            INDEX idx_form_id (form_id),
            INDEX idx_default (form_id, is_default)
        ) ENGINE=InnoDB
    ');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    -- Create records table
    SET @sql = CONCAT('
        CREATE TABLE IF NOT EXISTS ', app_schema_name, '.records (
            id VARCHAR(36) PRIMARY KEY,
            form_id VARCHAR(36) NOT NULL,
            data JSON NOT NULL,
            created_by VARCHAR(255),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
            INDEX idx_form_id (form_id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB
    ');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$

DELIMITER ;

-- =============================================================================
-- SAMPLE DATA (Optional - for testing)
-- =============================================================================

-- Create a sample app
-- INSERT INTO apps (id, name, description, schema_name, created_by)
-- VALUES ('app-001', 'CRM System', 'Customer Relationship Management', 'app_app_001', 'admin');

-- Create the app database
-- CALL create_app_database('app_app_001');

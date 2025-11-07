-- Checkpoint System Database Schema

-- Create checkpoint system database
CREATE DATABASE IF NOT EXISTS checkpoint_system;
USE checkpoint_system;

-- App registry
CREATE TABLE IF NOT EXISTS apps (
    app_id VARCHAR(255) PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('active', 'inactive') DEFAULT 'active',

    INDEX idx_schema (schema_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Checkpoint metadata (one per transaction)
CREATE TABLE IF NOT EXISTS checkpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,

    -- User-friendly metadata
    name VARCHAR(255),
    description TEXT,
    action_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Transaction identification
    gtid VARCHAR(255),
    xid BIGINT,
    transaction_length BIGINT,

    -- Binlog position
    binlog_filename VARCHAR(255) NOT NULL,
    binlog_start_position BIGINT NOT NULL,
    binlog_end_position BIGINT NOT NULL,

    -- Navigation
    parent_checkpoint_id BIGINT,

    INDEX idx_app_user (app_id, user_id, created_at),
    INDEX idx_gtid (gtid),
    INDEX idx_binlog (binlog_filename, binlog_start_position),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (parent_checkpoint_id) REFERENCES checkpoints(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transaction events (all events in one transaction)
CREATE TABLE IF NOT EXISTS checkpoint_transaction_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT NOT NULL,

    -- Event metadata
    schema_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    event_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    event_sequence INT NOT NULL,

    -- Event data (JSON with compression support)
    before_image TEXT,
    after_image TEXT,

    INDEX idx_checkpoint (checkpoint_id, event_sequence),
    INDEX idx_schema_table (schema_name, table_name),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- User's current checkpoint position (per app)
CREATE TABLE IF NOT EXISTS user_checkpoint_position (
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    current_checkpoint_id BIGINT NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (app_id, user_id),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (current_checkpoint_id) REFERENCES checkpoints(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

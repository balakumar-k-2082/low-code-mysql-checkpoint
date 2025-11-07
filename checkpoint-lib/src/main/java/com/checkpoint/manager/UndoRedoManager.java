package com.checkpoint.manager;

import com.checkpoint.model.Checkpoint;
import com.checkpoint.model.TransactionEvent;
import com.checkpoint.util.RowSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Handles undo and redo operations
 */
public class UndoRedoManager {

    private static final Logger logger = LoggerFactory.getLogger(UndoRedoManager.class);

    private final DataSource dataSource;
    private final CheckpointManager checkpointManager;
    private final String appId;
    private final String schemaName;

    public UndoRedoManager(DataSource dataSource, CheckpointManager checkpointManager,
                          String appId, String schemaName) {
        this.dataSource = dataSource;
        this.checkpointManager = checkpointManager;
        this.appId = appId;
        this.schemaName = schemaName;
    }

    /**
     * Undo - revert to previous checkpoint
     */
    public void undo(String userId) throws SQLException {
        // 1. Get current and previous checkpoints
        Checkpoint current = checkpointManager.getCurrentCheckpoint(userId);
        if (current == null) {
            throw new IllegalStateException("No current checkpoint found");
        }

        Checkpoint previous = checkpointManager.getPreviousCheckpoint(current);
        if (previous == null) {
            throw new IllegalStateException("Already at first checkpoint");
        }

        // 2. Load events for current transaction
        List<TransactionEvent> events = checkpointManager.loadTransactionEvents(current.getId());

        logger.info("Undo: reverting checkpoint {} -> {} ({} events)",
                   current.getName(), previous.getName(), events.size());

        // 3. Execute undo transaction
        executeUndoTransaction(events);

        // 4. Update user position
        updateUserPosition(userId, previous.getId());

        logger.info("Undo completed successfully");
    }

    /**
     * Redo - move forward to next checkpoint
     */
    public void redo(String userId) throws SQLException {
        // 1. Get current and next checkpoints
        Checkpoint current = checkpointManager.getCurrentCheckpoint(userId);
        if (current == null) {
            throw new IllegalStateException("No current checkpoint found");
        }

        Checkpoint next = checkpointManager.getNextCheckpoint(current);
        if (next == null) {
            throw new IllegalStateException("Already at latest checkpoint");
        }

        // 2. Load events for next transaction
        List<TransactionEvent> events = checkpointManager.loadTransactionEvents(next.getId());

        logger.info("Redo: applying checkpoint {} -> {} ({} events)",
                   current.getName(), next.getName(), events.size());

        // 3. Execute redo transaction
        executeRedoTransaction(events);

        // 4. Update user position
        updateUserPosition(userId, next.getId());

        logger.info("Redo completed successfully");
    }

    /**
     * Execute undo as a single atomic transaction
     */
    private void executeUndoTransaction(List<TransactionEvent> events) throws SQLException {
        Connection conn = dataSource.getConnection();

        try {
            // MUST set sql_log_bin BEFORE starting transaction
            executeUpdate(conn, "SET SESSION sql_log_bin = 0");
            executeUpdate(conn, "SET FOREIGN_KEY_CHECKS = 0");

            // NOW start the transaction
            conn.setAutoCommit(false);

            // Process events in REVERSE order
            for (int i = events.size() - 1; i >= 0; i--) {
                TransactionEvent event = events.get(i);
                String sql = generateUndoSql(event);

                logger.debug("Undo event {}: {} {}.{}",
                           i, event.getEventType(), event.getSchemaName(), event.getTableName());

                executeUpdateWithEvent(conn, sql, event);
            }

            conn.commit();

            logger.debug("Undo transaction committed successfully");

        } catch (Exception e) {
            conn.rollback();
            logger.error("Undo transaction failed, rolled back", e);
            throw new SQLException("Undo failed: " + e.getMessage(), e);

        } finally {
            // Re-enable settings
            try {
                executeUpdate(conn, "SET FOREIGN_KEY_CHECKS = 1");
                executeUpdate(conn, "SET SESSION sql_log_bin = 1");
            } catch (Exception e) {
                logger.warn("Failed to reset session variables", e);
            }
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    /**
     * Execute redo as a single atomic transaction
     */
    private void executeRedoTransaction(List<TransactionEvent> events) throws SQLException {
        Connection conn = dataSource.getConnection();

        try {
            // MUST set sql_log_bin BEFORE starting transaction
            executeUpdate(conn, "SET SESSION sql_log_bin = 0");
            executeUpdate(conn, "SET FOREIGN_KEY_CHECKS = 0");

            // NOW start the transaction
            conn.setAutoCommit(false);

            // Process events in FORWARD order
            for (int i = 0; i < events.size(); i++) {
                TransactionEvent event = events.get(i);
                String sql = generateRedoSql(event);

                logger.debug("Redo event {}: {} {}.{}",
                           i, event.getEventType(), event.getSchemaName(), event.getTableName());

                executeUpdateWithEvent(conn, sql, event);
            }

            conn.commit();

            logger.debug("Redo transaction committed successfully");

        } catch (Exception e) {
            conn.rollback();
            logger.error("Redo transaction failed, rolled back", e);
            throw new SQLException("Redo failed: " + e.getMessage(), e);

        } finally {
            // Re-enable settings
            try {
                executeUpdate(conn, "SET FOREIGN_KEY_CHECKS = 1");
                executeUpdate(conn, "SET SESSION sql_log_bin = 1");
            } catch (Exception e) {
                logger.warn("Failed to reset session variables", e);
            }
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    /**
     * Generate SQL for undo operation
     */
    private String generateUndoSql(TransactionEvent event) {
        String tableName = event.getSchemaName() + "." + event.getTableName();

        switch (event.getEventType()) {
            case "INSERT":
                // Reverse: DELETE the inserted row
                return "DELETE FROM " + tableName + " WHERE {pk_condition}";

            case "UPDATE":
                // Reverse: UPDATE with BEFORE image
                return "UPDATE " + tableName + " SET {columns} WHERE {pk_condition}";

            case "DELETE":
                // Reverse: INSERT the deleted row
                return "INSERT INTO " + tableName + " ({columns}) VALUES ({values})";

            default:
                throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }
    }

    /**
     * Generate SQL for redo operation
     */
    private String generateRedoSql(TransactionEvent event) {
        String tableName = event.getSchemaName() + "." + event.getTableName();

        switch (event.getEventType()) {
            case "INSERT":
                // Forward: INSERT with AFTER image
                return "INSERT INTO " + tableName + " ({columns}) VALUES ({values})";

            case "UPDATE":
                // Forward: UPDATE with AFTER image
                return "UPDATE " + tableName + " SET {columns} WHERE {pk_condition}";

            case "DELETE":
                // Forward: DELETE
                return "DELETE FROM " + tableName + " WHERE {pk_condition}";

            default:
                throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }
    }

    /**
     * Execute update with event data
     */
    private void executeUpdateWithEvent(Connection conn, String sqlTemplate, TransactionEvent event)
            throws SQLException {

        String sql;

        switch (event.getEventType()) {
            case "INSERT":
                // For undo: DELETE using AFTER image
                Map<String, Object> afterData = RowSerializer.deserializeRow(event.getAfterImage());
                sql = buildDeleteSql(sqlTemplate, afterData);
                break;

            case "UPDATE":
                // For undo: UPDATE using BEFORE image
                // For redo: UPDATE using AFTER image
                Map<String, Object> updateData = RowSerializer.deserializeRow(
                    sqlTemplate.contains("SET") ? event.getBeforeImage() : event.getAfterImage()
                );
                sql = buildUpdateSql(sqlTemplate, updateData);
                break;

            case "DELETE":
                // For undo: INSERT using BEFORE image
                // For redo: DELETE using BEFORE image
                if (sqlTemplate.startsWith("INSERT")) {
                    Map<String, Object> insertData = RowSerializer.deserializeRow(event.getBeforeImage());
                    sql = buildInsertSql(sqlTemplate, insertData);
                } else {
                    Map<String, Object> deleteData = RowSerializer.deserializeRow(event.getBeforeImage());
                    sql = buildDeleteSql(sqlTemplate, deleteData);
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Build DELETE SQL from template and data
     */
    private String buildDeleteSql(String template, Map<String, Object> data) {
        // Assume first column is primary key
        String pkColumn = data.keySet().iterator().next();
        Object pkValue = data.get(pkColumn);

        String pkCondition = pkColumn + " = " + formatValue(pkValue);
        return template.replace("{pk_condition}", pkCondition);
    }

    /**
     * Build UPDATE SQL from template and data
     */
    private String buildUpdateSql(String template, Map<String, Object> data) {
        StringBuilder setClauses = new StringBuilder();
        String pkColumn = null;
        Object pkValue = null;

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (pkColumn == null) {
                // First column is assumed to be primary key
                pkColumn = entry.getKey();
                pkValue = entry.getValue();
                continue;
            }

            if (!first) {
                setClauses.append(", ");
            }
            setClauses.append(entry.getKey())
                     .append(" = ")
                     .append(formatValue(entry.getValue()));
            first = false;
        }

        String pkCondition = pkColumn + " = " + formatValue(pkValue);

        return template.replace("{columns}", setClauses.toString())
                      .replace("{pk_condition}", pkCondition);
    }

    /**
     * Build INSERT SQL from template and data
     */
    private String buildInsertSql(String template, Map<String, Object> data) {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(entry.getKey());
            values.append(formatValue(entry.getValue()));
            first = false;
        }

        return template.replace("{columns}", columns.toString())
                      .replace("{values}", values.toString());
    }

    /**
     * Format value for SQL
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            // Escape single quotes
            String str = ((String) value).replace("'", "''");
            return "'" + str + "'";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        // Default: treat as string
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /**
     * Execute simple update
     */
    private void executeUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Update user checkpoint position
     */
    private void updateUserPosition(String userId, long checkpointId) throws SQLException {
        String sql = "UPDATE checkpoint_system.user_checkpoint_position " +
                    "SET current_checkpoint_id = ?, last_updated = NOW() " +
                    "WHERE app_id = ? AND user_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, checkpointId);
            stmt.setString(2, appId);
            stmt.setString(3, userId);

            stmt.executeUpdate();
        }
    }
}

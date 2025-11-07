package com.checkpoint.manager;

import com.checkpoint.capture.TransactionCapture;
import com.checkpoint.model.Checkpoint;
import com.checkpoint.model.Transaction;
import com.checkpoint.model.TransactionEvent;
import com.checkpoint.util.RowSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages checkpoints for transaction-based undo/redo
 */
public class CheckpointManager {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointManager.class);

    private final DataSource dataSource;
    private final String appId;
    private final String schemaName;
    private final TransactionCapture transactionCapture;

    public CheckpointManager(DataSource dataSource, String appId, String schemaName,
                            String binlogHost, int binlogPort, String binlogUser, String binlogPassword)
            throws SQLException {
        this.dataSource = dataSource;
        this.appId = appId;
        this.schemaName = schemaName;

        // Create transaction capture with callback
        Connection metadataConn = dataSource.getConnection();
        this.transactionCapture = new TransactionCapture(
            binlogHost, binlogPort, binlogUser, binlogPassword,
            schemaName, metadataConn
        );

        // Set callback for when transactions complete
        transactionCapture.setOnTransactionComplete(this::onTransactionComplete);
    }

    /**
     * Initialize schema and start capture
     */
    public void initialize() throws SQLException {
        initializeSchema();
        registerApp();

        // Get current binlog position
        BinlogPosition position = getCurrentBinlogPosition();
        transactionCapture.setBinlogPosition(position.filename, position.position);

        // Start capturing
        transactionCapture.start();

        logger.info("CheckpointManager initialized for app: {} schema: {}",
                   appId, schemaName);
    }

    /**
     * Initialize database schema
     */
    private void initializeSchema() throws SQLException {
        // In production, run schema.sql separately
        // For now, just verify tables exist
        logger.info("Verifying checkpoint system schema...");
    }

    /**
     * Register app in registry
     */
    private void registerApp() throws SQLException {
        String sql = "INSERT INTO checkpoint_system.apps " +
                    "(app_id, app_name, schema_name) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE schema_name = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            stmt.setString(2, appId);  // Use appId as name for now
            stmt.setString(3, schemaName);
            stmt.setString(4, schemaName);

            stmt.executeUpdate();

            logger.info("Registered app: {} with schema: {}", appId, schemaName);
        }
    }

    /**
     * Callback when a transaction completes
     */
    private void onTransactionComplete(Transaction transaction) {
        try {
            // For now, auto-create checkpoint for every transaction
            // In production, this would be controlled by the application
            createCheckpointFromTransaction(transaction, "auto_" + System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to create checkpoint from transaction", e);
        }
    }

    /**
     * Create checkpoint from a completed transaction
     */
    public long createCheckpointFromTransaction(Transaction transaction, String name)
            throws SQLException {

        String sql = "INSERT INTO checkpoint_system.checkpoints " +
                    "(app_id, user_id, name, gtid, xid, transaction_length, " +
                    "binlog_filename, binlog_start_position, binlog_end_position, " +
                    "parent_checkpoint_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long checkpointId;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, appId);
            stmt.setString(2, "system");  // Default user
            stmt.setString(3, name);
            stmt.setString(4, transaction.getGtid());
            stmt.setLong(5, transaction.getXid());
            stmt.setLong(6, transaction.getTransactionLength());
            stmt.setString(7, transaction.getBinlogFilename());
            stmt.setLong(8, transaction.getStartPosition());
            stmt.setLong(9, transaction.getEndPosition());

            // Get previous checkpoint ID
            Long previousId = getPreviousCheckpointId(appId, "system");
            if (previousId != null) {
                stmt.setLong(10, previousId);
            } else {
                stmt.setNull(10, Types.BIGINT);
            }

            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            rs.next();
            checkpointId = rs.getLong(1);
            rs.close();
        }

        // Store transaction events
        storeTransactionEvents(checkpointId, transaction.getEvents());

        // Update user position
        updateUserPosition(appId, "system", checkpointId);

        logger.info("Created checkpoint: id={}, gtid={}, events={}",
                   checkpointId, transaction.getGtid(), transaction.getEventCount());

        return checkpointId;
    }

    /**
     * Store transaction events
     */
    private void storeTransactionEvents(long checkpointId, List<TransactionEvent> events)
            throws SQLException {

        String sql = "INSERT INTO checkpoint_system.checkpoint_transaction_events " +
                    "(checkpoint_id, schema_name, table_name, event_type, " +
                    "event_sequence, before_image, after_image) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (TransactionEvent event : events) {
                stmt.setLong(1, checkpointId);
                stmt.setString(2, event.getSchemaName());
                stmt.setString(3, event.getTableName());
                stmt.setString(4, event.getEventType());
                stmt.setInt(5, event.getSequence());
                stmt.setString(6, event.getBeforeImage());
                stmt.setString(7, event.getAfterImage());

                stmt.addBatch();
            }

            stmt.executeBatch();
        }

        logger.debug("Stored {} events for checkpoint {}", events.size(), checkpointId);
    }

    /**
     * Get previous checkpoint ID for navigation
     */
    private Long getPreviousCheckpointId(String appId, String userId) throws SQLException {
        String sql = "SELECT id FROM checkpoint_system.checkpoints " +
                    "WHERE app_id = ? AND user_id = ? " +
                    "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            stmt.setString(2, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
            rs.close();
        }

        return null;
    }

    /**
     * Update user's current checkpoint position
     */
    private void updateUserPosition(String appId, String userId, long checkpointId)
            throws SQLException {

        String sql = "INSERT INTO checkpoint_system.user_checkpoint_position " +
                    "(app_id, user_id, current_checkpoint_id) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE current_checkpoint_id = ?, last_updated = NOW()";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            stmt.setString(2, userId);
            stmt.setLong(3, checkpointId);
            stmt.setLong(4, checkpointId);

            stmt.executeUpdate();
        }
    }

    /**
     * Get current binlog position from MySQL
     */
    private BinlogPosition getCurrentBinlogPosition() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW MASTER STATUS")) {

            if (rs.next()) {
                return new BinlogPosition(
                    rs.getString("File"),
                    rs.getLong("Position")
                );
            }
        }

        throw new SQLException("Failed to get binlog position");
    }

    /**
     * Get current checkpoint for user
     */
    public Checkpoint getCurrentCheckpoint(String userId) throws SQLException {
        String sql = "SELECT c.* FROM checkpoint_system.checkpoints c " +
                    "JOIN checkpoint_system.user_checkpoint_position p " +
                    "  ON c.id = p.current_checkpoint_id " +
                    "WHERE p.app_id = ? AND p.user_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            stmt.setString(2, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapCheckpoint(rs);
            }
            rs.close();
        }

        return null;
    }

    /**
     * Get previous checkpoint
     */
    public Checkpoint getPreviousCheckpoint(Checkpoint current) throws SQLException {
        if (current.getParentCheckpointId() == null) {
            return null;
        }

        String sql = "SELECT * FROM checkpoint_system.checkpoints WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, current.getParentCheckpointId());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapCheckpoint(rs);
            }
            rs.close();
        }

        return null;
    }

    /**
     * Get next checkpoint
     */
    public Checkpoint getNextCheckpoint(Checkpoint current) throws SQLException {
        String sql = "SELECT * FROM checkpoint_system.checkpoints " +
                    "WHERE parent_checkpoint_id = ? AND app_id = ? " +
                    "ORDER BY created_at ASC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, current.getId());
            stmt.setString(2, appId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapCheckpoint(rs);
            }
            rs.close();
        }

        return null;
    }

    /**
     * Load transaction events for a checkpoint
     */
    public List<TransactionEvent> loadTransactionEvents(long checkpointId) throws SQLException {
        List<TransactionEvent> events = new ArrayList<>();

        String sql = "SELECT * FROM checkpoint_system.checkpoint_transaction_events " +
                    "WHERE checkpoint_id = ? ORDER BY event_sequence ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, checkpointId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TransactionEvent event = new TransactionEvent();
                event.setEventType(rs.getString("event_type"));
                event.setSchemaName(rs.getString("schema_name"));
                event.setTableName(rs.getString("table_name"));
                event.setSequence(rs.getInt("event_sequence"));
                event.setBeforeImage(rs.getString("before_image"));
                event.setAfterImage(rs.getString("after_image"));

                events.add(event);
            }
            rs.close();
        }

        return events;
    }

    /**
     * Map ResultSet to Checkpoint object
     */
    private Checkpoint mapCheckpoint(ResultSet rs) throws SQLException {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(rs.getLong("id"));
        checkpoint.setAppId(rs.getString("app_id"));
        checkpoint.setUserId(rs.getString("user_id"));
        checkpoint.setName(rs.getString("name"));
        checkpoint.setDescription(rs.getString("description"));
        checkpoint.setActionType(rs.getString("action_type"));
        checkpoint.setCreatedAt(rs.getTimestamp("created_at"));
        checkpoint.setGtid(rs.getString("gtid"));
        checkpoint.setXid(rs.getLong("xid"));
        checkpoint.setTransactionLength(rs.getLong("transaction_length"));
        checkpoint.setBinlogFilename(rs.getString("binlog_filename"));
        checkpoint.setBinlogStartPosition(rs.getLong("binlog_start_position"));
        checkpoint.setBinlogEndPosition(rs.getLong("binlog_end_position"));

        long parentId = rs.getLong("parent_checkpoint_id");
        if (!rs.wasNull()) {
            checkpoint.setParentCheckpointId(parentId);
        }

        return checkpoint;
    }

    /**
     * Stop checkpoint manager
     */
    public void stop() {
        transactionCapture.stop();
        logger.info("CheckpointManager stopped");
    }

    // Inner class for binlog position
    private static class BinlogPosition {
        final String filename;
        final long position;

        BinlogPosition(String filename, long position) {
            this.filename = filename;
            this.position = position;
        }
    }
}

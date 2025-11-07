package com.checkpoint.capture;

import com.checkpoint.model.TableMetadata;
import com.checkpoint.model.Transaction;
import com.checkpoint.model.TransactionEvent;
import com.checkpoint.util.RowSerializer;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Captures MySQL transactions from binlog
 * Detects transaction boundaries using GTID, BEGIN, COMMIT, XID events
 */
public class TransactionCapture {

    private static final Logger logger = LoggerFactory.getLogger(TransactionCapture.class);

    private final BinaryLogClient client;
    private final TableMapper tableMapper;
    private final String targetSchemaName;
    private final Connection metadataConnection;

    // Current transaction being captured
    private Transaction currentTransaction = null;
    private String currentGtid = null;
    private String currentBinlogFilename;

    // Callback when transaction completes
    private Consumer<Transaction> onTransactionComplete;

    // Column name cache
    private final Map<String, List<String>> columnNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    public TransactionCapture(String host, int port, String username, String password,
                             String targetSchemaName, Connection metadataConnection) {
        this.client = new BinaryLogClient(host, port, username, password);
        this.tableMapper = new TableMapper();
        this.targetSchemaName = targetSchemaName;
        this.metadataConnection = metadataConnection;

        setupEventListener();
    }

    /**
     * Set callback for when a transaction is completed
     */
    public void setOnTransactionComplete(Consumer<Transaction> callback) {
        this.onTransactionComplete = callback;
    }

    /**
     * Set binlog position to start from
     */
    public void setBinlogPosition(String filename, long position) {
        client.setBinlogFilename(filename);
        client.setBinlogPosition(position);
        this.currentBinlogFilename = filename;
    }

    /**
     * Setup event listener for binlog events
     */
    private void setupEventListener() {
        client.registerEventListener(event -> {
            try {
                EventHeader header = event.getHeader();
                EventType eventType = header.getEventType();
                EventData data = event.getData();

                // Update current binlog filename on rotation
                if (eventType == EventType.ROTATE) {
                    RotateEventData rotateData = (RotateEventData) data;
                    currentBinlogFilename = rotateData.getBinlogFilename();
                    logger.info("Binlog rotated to: {}", currentBinlogFilename);
                }

                // Handle different event types
                switch (eventType) {
                    case GTID:
                        handleGtidEvent((GtidEventData) data);
                        break;

                    case QUERY:
                        handleQueryEvent((QueryEventData) data);
                        break;

                    case TABLE_MAP:
                        handleTableMapEvent((TableMapEventData) data);
                        break;

                    case EXT_WRITE_ROWS:
                    case WRITE_ROWS:
                        handleWriteRowsEvent((WriteRowsEventData) data);
                        break;

                    case EXT_UPDATE_ROWS:
                    case UPDATE_ROWS:
                        handleUpdateRowsEvent((UpdateRowsEventData) data);
                        break;

                    case EXT_DELETE_ROWS:
                    case DELETE_ROWS:
                        handleDeleteRowsEvent((DeleteRowsEventData) data);
                        break;

                    case XID:
                        handleXidEvent((XidEventData) data);
                        break;

                    default:
                        // Ignore other event types
                        break;
                }

            } catch (Exception e) {
                logger.error("Error processing binlog event", e);
            }
        });
    }

    /**
     * Handle GTID event - marks start of transaction
     */
    private void handleGtidEvent(GtidEventData data) {
        currentGtid = data.getGtid(); // deprecated, but still used for compatibility

        // Start new transaction
        currentTransaction = new Transaction();
        currentTransaction.setGtid(currentGtid);
        currentTransaction.setStartPosition(client.getBinlogPosition());
        currentTransaction.setBinlogFilename(currentBinlogFilename);

        logger.debug("Transaction started: GTID={}", currentGtid);
    }

    /**
     * Handle QUERY event - can be BEGIN, COMMIT, ROLLBACK, or DDL
     */
    private void handleQueryEvent(QueryEventData data) {
        String sql = data.getSql().trim().toUpperCase();

        if (sql.equals("BEGIN")) {
            // Transaction started (if no GTID, this marks start)
            if (currentTransaction == null) {
                currentTransaction = new Transaction();
                currentTransaction.setStartPosition(client.getBinlogPosition());
                currentTransaction.setBinlogFilename(currentBinlogFilename);
            }
            logger.debug("Transaction BEGIN");

        } else if (sql.equals("COMMIT")) {
            // Transaction committed (for non-XA transactions)
            if (currentTransaction != null) {
                currentTransaction.setCommitted(true);
                currentTransaction.setEndPosition(client.getBinlogPosition());
                completeTransaction();
            }
            logger.debug("Transaction COMMIT");

        } else if (sql.equals("ROLLBACK")) {
            // Transaction rolled back - discard
            if (currentTransaction != null) {
                logger.info("Transaction ROLLBACK: GTID={}", currentTransaction.getGtid());
                currentTransaction = null;
                currentGtid = null;
            }

        } else if (sql.startsWith("CREATE") || sql.startsWith("ALTER") ||
                   sql.startsWith("DROP") || sql.startsWith("TRUNCATE")) {
            // DDL statement - auto-commit transaction
            logger.info("DDL statement: {}", sql.substring(0, Math.min(sql.length(), 100)));

            Transaction ddlTransaction = new Transaction();
            ddlTransaction.setDdl(true);
            ddlTransaction.setDdlSql(sql);
            ddlTransaction.setStartPosition(client.getBinlogPosition());
            ddlTransaction.setEndPosition(client.getBinlogPosition());
            ddlTransaction.setBinlogFilename(currentBinlogFilename);
            ddlTransaction.setCommitted(true);

            // DDL transactions are auto-committed
            if (onTransactionComplete != null) {
                onTransactionComplete.accept(ddlTransaction);
            }
        }
    }

    /**
     * Handle TABLE_MAP event - provides metadata for upcoming row events
     */
    private void handleTableMapEvent(TableMapEventData data) {
        tableMapper.registerTable(data);
    }

    /**
     * Handle INSERT events
     */
    private void handleWriteRowsEvent(WriteRowsEventData data) {
        if (currentTransaction == null) {
            return;
        }

        long tableId = data.getTableId();

        // Filter by schema
        if (!tableMapper.isFromSchema(tableId, targetSchemaName)) {
            return;
        }

        TableMetadata table = tableMapper.getTable(tableId);
        if (table == null) {
            logger.warn("Table metadata not found for tableId: {}", tableId);
            return;
        }

        List<Serializable[]> rows = data.getRows();
        List<String> columnNames = getColumnNames(table);

        for (Serializable[] row : rows) {
            TransactionEvent event = new TransactionEvent();
            event.setEventType("INSERT");
            event.setSchemaName(table.getSchemaName());
            event.setTableName(table.getTableName());
            event.setBeforeImage(null);
            event.setAfterImage(RowSerializer.serializeRow(table.getTableName(), row, columnNames));

            currentTransaction.addEvent(event);

            logger.debug("Captured INSERT: {}.{}", table.getSchemaName(), table.getTableName());
        }
    }

    /**
     * Handle UPDATE events - has BEFORE and AFTER images
     */
    private void handleUpdateRowsEvent(UpdateRowsEventData data) {
        if (currentTransaction == null) {
            return;
        }

        long tableId = data.getTableId();

        // Filter by schema
        if (!tableMapper.isFromSchema(tableId, targetSchemaName)) {
            return;
        }

        TableMetadata table = tableMapper.getTable(tableId);
        if (table == null) {
            logger.warn("Table metadata not found for tableId: {}", tableId);
            return;
        }

        List<Map.Entry<Serializable[], Serializable[]>> rows = data.getRows();
        List<String> columnNames = getColumnNames(table);

        for (Map.Entry<Serializable[], Serializable[]> row : rows) {
            TransactionEvent event = new TransactionEvent();
            event.setEventType("UPDATE");
            event.setSchemaName(table.getSchemaName());
            event.setTableName(table.getTableName());
            event.setBeforeImage(RowSerializer.serializeRow(table.getTableName(), row.getKey(), columnNames));
            event.setAfterImage(RowSerializer.serializeRow(table.getTableName(), row.getValue(), columnNames));

            currentTransaction.addEvent(event);

            logger.debug("Captured UPDATE: {}.{}", table.getSchemaName(), table.getTableName());
        }
    }

    /**
     * Handle DELETE events
     */
    private void handleDeleteRowsEvent(DeleteRowsEventData data) {
        if (currentTransaction == null) {
            return;
        }

        long tableId = data.getTableId();

        // Filter by schema
        if (!tableMapper.isFromSchema(tableId, targetSchemaName)) {
            return;
        }

        TableMetadata table = tableMapper.getTable(tableId);
        if (table == null) {
            logger.warn("Table metadata not found for tableId: {}", tableId);
            return;
        }

        List<Serializable[]> rows = data.getRows();
        List<String> columnNames = getColumnNames(table);

        for (Serializable[] row : rows) {
            TransactionEvent event = new TransactionEvent();
            event.setEventType("DELETE");
            event.setSchemaName(table.getSchemaName());
            event.setTableName(table.getTableName());
            event.setBeforeImage(RowSerializer.serializeRow(table.getTableName(), row, columnNames));
            event.setAfterImage(null);

            currentTransaction.addEvent(event);

            logger.debug("Captured DELETE: {}.{}", table.getSchemaName(), table.getTableName());
        }
    }

    /**
     * Handle XID event - marks end of XA transaction (most common)
     */
    private void handleXidEvent(XidEventData data) {
        if (currentTransaction != null) {
            currentTransaction.setXid(data.getXid());
            currentTransaction.setEndPosition(client.getBinlogPosition());
            currentTransaction.setCommitted(true);

            completeTransaction();

            logger.debug("Transaction committed: XID={}", data.getXid());
        }
    }

    /**
     * Complete current transaction and invoke callback
     */
    private void completeTransaction() {
        if (currentTransaction == null || !currentTransaction.isCommitted()) {
            return;
        }

        // Only notify if transaction has events
        if (!currentTransaction.isEmpty()) {
            logger.info("Transaction complete: GTID={}, events={}",
                       currentTransaction.getGtid(),
                       currentTransaction.getEventCount());

            if (onTransactionComplete != null) {
                onTransactionComplete.accept(currentTransaction);
            }
        }

        // Clear current transaction
        currentTransaction = null;
        currentGtid = null;
    }

    /**
     * Get column names for a table (with caching)
     */
    private List<String> getColumnNames(TableMetadata table) {
        String key = table.getFullTableName();

        return columnNameCache.computeIfAbsent(key, k ->
            RowSerializer.getColumnNames(metadataConnection, table.getSchemaName(), table.getTableName())
        );
    }

    /**
     * Start capturing binlog events
     */
    public void start() {
        Thread captureThread = new Thread(() -> {
            try {
                logger.info("Starting binlog capture from {}:{}",
                           client.getBinlogFilename(), client.getBinlogPosition());
                client.connect();
            } catch (IOException e) {
                logger.error("Failed to connect to binlog", e);
            }
        });

        captureThread.setName("BinlogCapture");
        captureThread.setDaemon(false);
        captureThread.start();
    }

    /**
     * Stop capturing
     */
    public void stop() {
        try {
            client.disconnect();
            logger.info("Binlog capture stopped");
        } catch (IOException e) {
            logger.error("Error stopping binlog capture", e);
        }
    }

    /**
     * Get current binlog position
     */
    public long getCurrentPosition() {
        return client.getBinlogPosition();
    }

    /**
     * Get current binlog filename
     */
    public String getCurrentFilename() {
        return currentBinlogFilename;
    }

    /**
     * Get table mapper
     */
    public TableMapper getTableMapper() {
        return tableMapper;
    }
}

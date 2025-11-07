package com.checkpoint.model;

/**
 * Represents a single event (INSERT/UPDATE/DELETE) within a transaction
 */
public class TransactionEvent {

    private String eventType;      // INSERT, UPDATE, DELETE
    private String schemaName;
    private String tableName;
    private int sequence;          // Order within transaction
    private String beforeImage;    // JSON representation (for UPDATE/DELETE)
    private String afterImage;     // JSON representation (for INSERT/UPDATE)
    private long binlogPosition;

    public TransactionEvent() {
    }

    public TransactionEvent(String eventType, String schemaName, String tableName) {
        this.eventType = eventType;
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    // Getters and setters

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getBeforeImage() {
        return beforeImage;
    }

    public void setBeforeImage(String beforeImage) {
        this.beforeImage = beforeImage;
    }

    public String getAfterImage() {
        return afterImage;
    }

    public void setAfterImage(String afterImage) {
        this.afterImage = afterImage;
    }

    public long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    @Override
    public String toString() {
        return "TransactionEvent{" +
               "type='" + eventType + '\'' +
               ", table='" + schemaName + "." + tableName + '\'' +
               ", sequence=" + sequence +
               '}';
    }
}

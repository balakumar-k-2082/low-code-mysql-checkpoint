package com.checkpoint.model;

/**
 * Metadata for a database table extracted from TABLE_MAP event
 */
public class TableMetadata {

    private final String schemaName;
    private final String tableName;
    private final long tableId;

    public TableMetadata(String schemaName, String tableName, long tableId) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableId = tableId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public long getTableId() {
        return tableId;
    }

    public String getFullTableName() {
        return schemaName + "." + tableName;
    }

    @Override
    public String toString() {
        return "TableMetadata{" +
               "schema='" + schemaName + '\'' +
               ", table='" + tableName + '\'' +
               ", tableId=" + tableId +
               '}';
    }
}

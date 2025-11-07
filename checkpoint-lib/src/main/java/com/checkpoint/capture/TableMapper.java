package com.checkpoint.capture;

import com.checkpoint.model.TableMetadata;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps table IDs to schema and table names from TABLE_MAP events
 */
public class TableMapper {

    private static final Logger logger = LoggerFactory.getLogger(TableMapper.class);

    // Maps table ID to table metadata
    private final Map<Long, TableMetadata> tableMap = new ConcurrentHashMap<>();

    /**
     * Register a table mapping from TABLE_MAP event
     *
     * @param data TableMapEventData from binlog
     */
    public void registerTable(TableMapEventData data) {
        TableMetadata metadata = new TableMetadata(
            data.getDatabase(),  // Schema name
            data.getTable(),     // Table name
            data.getTableId()    // Table ID
        );

        tableMap.put(data.getTableId(), metadata);

        logger.debug("Registered table: {} (tableId={})",
                    metadata.getFullTableName(), data.getTableId());
    }

    /**
     * Get table metadata by table ID
     *
     * @param tableId Table ID from row event
     * @return TableMetadata or null if not found
     */
    public TableMetadata getTable(long tableId) {
        return tableMap.get(tableId);
    }

    /**
     * Check if a table ID belongs to a specific schema
     *
     * @param tableId Table ID
     * @param schemaName Schema name to check
     * @return true if table belongs to schema, false otherwise
     */
    public boolean isFromSchema(long tableId, String schemaName) {
        TableMetadata metadata = tableMap.get(tableId);
        return metadata != null && metadata.getSchemaName().equals(schemaName);
    }

    /**
     * Clear all table mappings
     */
    public void clear() {
        int size = tableMap.size();
        tableMap.clear();
        logger.info("Cleared {} table mappings", size);
    }

    /**
     * Get number of registered tables
     *
     * @return Number of tables in map
     */
    public int size() {
        return tableMap.size();
    }

    /**
     * Cleanup stale table mappings (tables that no longer exist)
     * This should be called periodically to prevent memory leaks
     *
     * @param activeTableIds Set of active table IDs from database
     */
    public void cleanup(java.util.Set<Long> activeTableIds) {
        int before = tableMap.size();
        tableMap.keySet().retainAll(activeTableIds);
        int removed = before - tableMap.size();

        if (removed > 0) {
            logger.info("Cleaned up {} stale table mappings", removed);
        }
    }
}

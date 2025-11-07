package com.checkpoint.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.*;
import java.util.*;

/**
 * Utility for serializing database rows to/from JSON
 */
public class RowSerializer {

    private static final Logger logger = LoggerFactory.getLogger(RowSerializer.class);
    private static final Gson gson = new GsonBuilder()
                                        .serializeNulls()
                                        .create();

    /**
     * Serialize a row (from binlog) to JSON
     *
     * @param tableName Table name (for column metadata lookup)
     * @param row Row data as Serializable array
     * @param columnNames List of column names in order
     * @return JSON string representation
     */
    public static String serializeRow(String tableName, Serializable[] row, List<String> columnNames) {
        if (row == null) {
            return null;
        }

        Map<String, Object> rowData = new LinkedHashMap<>();

        for (int i = 0; i < columnNames.size() && i < row.length; i++) {
            String columnName = columnNames.get(i);
            Object value = row[i];

            // Convert special types to JSON-friendly format
            if (value instanceof byte[]) {
                // Convert byte array to Base64 for JSON
                value = Base64.getEncoder().encodeToString((byte[]) value);
            } else if (value instanceof java.sql.Date ||
                      value instanceof java.sql.Time ||
                      value instanceof java.sql.Timestamp) {
                // Convert to ISO string
                value = value.toString();
            }

            rowData.put(columnName, value);
        }

        return gson.toJson(rowData);
    }

    /**
     * Deserialize JSON back to a map
     *
     * @param json JSON string
     * @return Map of column name to value
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deserializeRow(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        return gson.fromJson(json, Map.class);
    }

    /**
     * Get column names for a table from database metadata
     *
     * @param connection Database connection
     * @param schemaName Schema name
     * @param tableName Table name
     * @return List of column names in order
     */
    public static List<String> getColumnNames(Connection connection, String schemaName, String tableName) {
        List<String> columnNames = new ArrayList<>();

        try {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet rs = metadata.getColumns(schemaName, null, tableName, null);

            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                int position = rs.getInt("ORDINAL_POSITION");
                columnNames.add(columnName);
            }

            rs.close();

            logger.debug("Retrieved {} columns for table {}.{}",
                        columnNames.size(), schemaName, tableName);

        } catch (SQLException e) {
            logger.error("Failed to get column names for {}.{}", schemaName, tableName, e);
        }

        return columnNames;
    }

    /**
     * Get primary key column name for a table
     *
     * @param connection Database connection
     * @param schemaName Schema name
     * @param tableName Table name
     * @return Primary key column name or "id" as fallback
     */
    public static String getPrimaryKeyColumn(Connection connection, String schemaName, String tableName) {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet rs = metadata.getPrimaryKeys(schemaName, null, tableName);

            if (rs.next()) {
                String pkColumn = rs.getString("COLUMN_NAME");
                rs.close();
                return pkColumn;
            }

            rs.close();

        } catch (SQLException e) {
            logger.error("Failed to get primary key for {}.{}", schemaName, tableName, e);
        }

        // Fallback to common primary key name
        return "id";
    }

    /**
     * Compress JSON string using GZIP (for large data)
     *
     * @param json JSON string
     * @return Compressed and Base64 encoded string
     */
    public static String compressJson(String json) {
        if (json == null || json.length() < 1024) {
            // Don't compress small strings
            return json;
        }

        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos);
            gzipOut.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            gzipOut.close();

            byte[] compressed = baos.toByteArray();
            String encoded = Base64.getEncoder().encodeToString(compressed);

            logger.debug("Compressed JSON from {} bytes to {} bytes",
                        json.length(), compressed.length);

            return "GZIP:" + encoded;

        } catch (Exception e) {
            logger.error("Failed to compress JSON", e);
            return json;
        }
    }

    /**
     * Decompress GZIP compressed JSON
     *
     * @param compressed Compressed string (with "GZIP:" prefix)
     * @return Original JSON string
     */
    public static String decompressJson(String compressed) {
        if (compressed == null || !compressed.startsWith("GZIP:")) {
            return compressed;
        }

        try {
            String encoded = compressed.substring(5);  // Remove "GZIP:" prefix
            byte[] compressedBytes = Base64.getDecoder().decode(encoded);

            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressedBytes);
            java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(bais);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            gzipIn.close();
            String decompressed = baos.toString(java.nio.charset.StandardCharsets.UTF_8.name());

            logger.debug("Decompressed JSON from {} bytes to {} bytes",
                        compressed.length(), decompressed.length());

            return decompressed;

        } catch (Exception e) {
            logger.error("Failed to decompress JSON", e);
            return compressed;
        }
    }
}

# Multi-Schema Architecture for Low-Code Platform

## Executive Summary

In a **low-code platform**, each application gets its own MySQL schema (database). This document describes how to implement checkpoint/undo/redo functionality with **per-app isolation** using binlog filtering by database schema.

**Architecture:**
```
MySQL Server
├── app_crm (schema for CRM app)
│   ├── customers
│   ├── orders
│   └── form_fields
├── app_inventory (schema for Inventory app)
│   ├── products
│   ├── warehouses
│   └── form_fields
├── app_hr (schema for HR app)
│   ├── employees
│   ├── departments
│   └── form_fields
└── checkpoint_system (shared schema for checkpoint metadata)
    ├── checkpoints
    ├── checkpoint_events
    ├── user_checkpoint_position
    └── app_registry
```

**Key Principle:** Each app's binlog events are filtered by schema name, ensuring complete isolation.

---

## Architecture Overview

### 1. One Schema Per App

```
Low-Code Platform
│
├── App 1: CRM
│   └── MySQL Schema: app_crm
│       ├── Application Tables (customers, orders, ...)
│       └── Binlog captures changes ONLY to app_crm.*
│
├── App 2: Inventory
│   └── MySQL Schema: app_inventory
│       ├── Application Tables (products, warehouses, ...)
│       └── Binlog captures changes ONLY to app_inventory.*
│
└── App 3: HR
    └── MySQL Schema: app_hr
        ├── Application Tables (employees, departments, ...)
        └── Binlog captures changes ONLY to app_hr.*
```

### 2. Shared Checkpoint System Schema

```sql
-- Dedicated schema for checkpoint metadata (shared across all apps)
CREATE DATABASE checkpoint_system;

USE checkpoint_system;

-- App registry
CREATE TABLE apps (
    app_id VARCHAR(255) PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('active', 'inactive') DEFAULT 'active',

    INDEX idx_schema (schema_name)
);

-- Checkpoint metadata (per app, per user)
CREATE TABLE checkpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(255) NOT NULL,           -- Which app
    user_id VARCHAR(255) NOT NULL,          -- Which user
    name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Binlog position
    binlog_filename VARCHAR(255) NOT NULL,
    binlog_position BIGINT NOT NULL,
    gtid_set TEXT,

    -- Navigation
    parent_checkpoint_id BIGINT,

    INDEX idx_app_user (app_id, user_id, created_at),
    INDEX idx_binlog (binlog_filename, binlog_position),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (parent_checkpoint_id) REFERENCES checkpoints(id)
);

-- Captured events (filtered by schema)
CREATE TABLE checkpoint_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT NOT NULL,

    -- Event metadata
    schema_name VARCHAR(255) NOT NULL,      -- CRITICAL: Which schema/app
    table_name VARCHAR(255) NOT NULL,
    event_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    timestamp BIGINT NOT NULL,
    binlog_position BIGINT NOT NULL,

    -- Event data (from ROW format with FULL image)
    before_image JSON,
    after_image JSON,

    -- Ordering
    event_sequence INT NOT NULL,

    INDEX idx_checkpoint (checkpoint_id, event_sequence),
    INDEX idx_schema_table (schema_name, table_name),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
);

-- User's current position (per app)
CREATE TABLE user_checkpoint_position (
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    current_checkpoint_id BIGINT NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (app_id, user_id),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (current_checkpoint_id) REFERENCES checkpoints(id)
);

-- Version history (per app)
CREATE TABLE version_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    checkpoint_id BIGINT NOT NULL,
    schema_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    row_identifier VARCHAR(255) NOT NULL,
    change_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    before_data JSON,
    after_data JSON,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_app_user_table (app_id, user_id, table_name),
    INDEX idx_checkpoint (checkpoint_id),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id)
);
```

---

## Binlog Event Filtering by Schema

### Problem: Single Binlog Contains All Schemas

```
MySQL Binlog (mysql-bin.000001)
─────────────────────────────────────────────────────────
Position 1000: INSERT into app_crm.customers ...        ← App 1
Position 1500: UPDATE app_inventory.products ...        ← App 2
Position 2000: INSERT into app_hr.employees ...         ← App 3
Position 2500: UPDATE app_crm.orders ...                ← App 1
Position 3000: DELETE from app_inventory.stock ...      ← App 2
─────────────────────────────────────────────────────────
```

**Challenge:** We need to capture ONLY events for a specific app's schema.

### Solution: Application-Level Filtering

**Key Insight:** Each row event is preceded by a `TABLE_MAP` event that contains:
- Database/schema name
- Table name
- Table ID (used by subsequent row events)

**Filtering Strategy:**
1. Listen for `TABLE_MAP` events
2. Store mapping: `tableId → {schema, table}`
3. For each row event, check if schema matches app's schema
4. Only process events for the target schema

---

## Java Implementation with Schema Filtering

### 1. Table Mapping Tracker

```java
import com.github.shyiko.mysql.binlog.event.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TableMapper {

    // Maps table ID to schema and table metadata
    private Map<Long, TableMetadata> tableMap = new ConcurrentHashMap<>();

    public static class TableMetadata {
        private final String schemaName;
        private final String tableName;
        private final long tableId;

        public TableMetadata(String schemaName, String tableName, long tableId) {
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.tableId = tableId;
        }

        public String getSchemaName() { return schemaName; }
        public String getTableName() { return tableName; }
        public long getTableId() { return tableId; }

        public String getFullTableName() {
            return schemaName + "." + tableName;
        }
    }

    /**
     * Register table mapping from TABLE_MAP event
     */
    public void registerTable(TableMapEventData data) {
        TableMetadata metadata = new TableMetadata(
            data.getDatabase(),  // Schema name
            data.getTable(),     // Table name
            data.getTableId()    // Table ID
        );

        tableMap.put(data.getTableId(), metadata);
    }

    /**
     * Get table metadata by table ID
     */
    public TableMetadata getTable(long tableId) {
        return tableMap.get(tableId);
    }

    /**
     * Check if table belongs to specific schema
     */
    public boolean isFromSchema(long tableId, String schemaName) {
        TableMetadata metadata = tableMap.get(tableId);
        return metadata != null && metadata.getSchemaName().equals(schemaName);
    }
}
```

### 2. App-Specific Checkpoint Manager

```java
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;

public class AppCheckpointManager {

    private final String appId;
    private final String schemaName;
    private final TableMapper tableMapper;
    private BinaryLogClient client;
    private long currentCheckpointId;
    private int eventSequence = 0;

    public AppCheckpointManager(String appId, String schemaName) {
        this.appId = appId;
        this.schemaName = schemaName;
        this.tableMapper = new TableMapper();
    }

    /**
     * Start capturing binlog events for this app's schema only
     */
    public void startEventCapture(String host, int port, String user, String password,
                                  String binlogFilename, long binlogPosition) {

        client = new BinaryLogClient(host, port, user, password);
        client.setBinlogFilename(binlogFilename);
        client.setBinlogPosition(binlogPosition);

        // Register event listener with schema filtering
        client.registerEventListener(event -> {
            EventData data = event.getData();

            if (data instanceof TableMapEventData) {
                // Track table mapping
                TableMapEventData tableMapData = (TableMapEventData) data;
                tableMapper.registerTable(tableMapData);

                // Log for debugging
                System.out.println("TABLE_MAP: " +
                                 tableMapData.getDatabase() + "." +
                                 tableMapData.getTable() +
                                 " (tableId=" + tableMapData.getTableId() + ")");

            } else if (data instanceof WriteRowsEventData) {
                handleInsertEvent((WriteRowsEventData) data);

            } else if (data instanceof UpdateRowsEventData) {
                handleUpdateEvent((UpdateRowsEventData) data);

            } else if (data instanceof DeleteRowsEventData) {
                handleDeleteEvent((DeleteRowsEventData) data);
            }
        });

        // Start listening
        new Thread(() -> {
            try {
                client.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleInsertEvent(WriteRowsEventData data) {
        long tableId = data.getTableId();

        // CRITICAL: Filter by schema
        if (!tableMapper.isFromSchema(tableId, schemaName)) {
            return;  // Ignore events from other schemas
        }

        TableMapper.TableMetadata table = tableMapper.getTable(tableId);
        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows) {
            String afterImage = serializeRow(table.getTableName(), row);

            // Store event with schema information
            storeEvent(
                currentCheckpointId,
                schemaName,          // Store schema name
                table.getTableName(),
                "INSERT",
                null,
                afterImage,
                eventSequence++
            );

            System.out.println("✅ Captured INSERT for " + appId + ": " +
                             table.getFullTableName());
        }
    }

    private void handleUpdateEvent(UpdateRowsEventData data) {
        long tableId = data.getTableId();

        // CRITICAL: Filter by schema
        if (!tableMapper.isFromSchema(tableId, schemaName)) {
            return;  // Ignore events from other schemas
        }

        TableMapper.TableMetadata table = tableMapper.getTable(tableId);
        List<Map.Entry<Serializable[], Serializable[]>> rows = data.getRows();

        for (Map.Entry<Serializable[], Serializable[]> row : rows) {
            String beforeImage = serializeRow(table.getTableName(), row.getKey());
            String afterImage = serializeRow(table.getTableName(), row.getValue());

            storeEvent(
                currentCheckpointId,
                schemaName,          // Store schema name
                table.getTableName(),
                "UPDATE",
                beforeImage,
                afterImage,
                eventSequence++
            );

            System.out.println("✅ Captured UPDATE for " + appId + ": " +
                             table.getFullTableName());
        }
    }

    private void handleDeleteEvent(DeleteRowsEventData data) {
        long tableId = data.getTableId();

        // CRITICAL: Filter by schema
        if (!tableMapper.isFromSchema(tableId, schemaName)) {
            return;  // Ignore events from other schemas
        }

        TableMapper.TableMetadata table = tableMapper.getTable(tableId);
        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows) {
            String beforeImage = serializeRow(table.getTableName(), row);

            storeEvent(
                currentCheckpointId,
                schemaName,          // Store schema name
                table.getTableName(),
                "DELETE",
                beforeImage,
                null,
                eventSequence++
            );

            System.out.println("✅ Captured DELETE for " + appId + ": " +
                             table.getFullTableName());
        }
    }

    private void storeEvent(long checkpointId, String schemaName, String tableName,
                           String eventType, String beforeImage, String afterImage,
                           int sequence) {
        String sql = "INSERT INTO checkpoint_system.checkpoint_events " +
                    "(checkpoint_id, schema_name, table_name, event_type, " +
                    "before_image, after_image, event_sequence, timestamp, binlog_position) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, checkpointId);
            stmt.setString(2, schemaName);     // CRITICAL: Store schema
            stmt.setString(3, tableName);
            stmt.setString(4, eventType);
            stmt.setString(5, beforeImage);
            stmt.setString(6, afterImage);
            stmt.setInt(7, sequence);
            stmt.setLong(8, System.currentTimeMillis());
            stmt.setLong(9, getCurrentBinlogPosition());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```

### 3. Multi-App Checkpoint System

```java
public class MultiAppCheckpointSystem {

    private Map<String, AppCheckpointManager> appManagers = new ConcurrentHashMap<>();

    /**
     * Register an app in the system
     */
    public void registerApp(String appId, String appName, String schemaName) {
        String sql = "INSERT INTO checkpoint_system.apps " +
                    "(app_id, app_name, schema_name) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            stmt.setString(2, appName);
            stmt.setString(3, schemaName);
            stmt.executeUpdate();

            System.out.println("✅ Registered app: " + appName + " (schema: " + schemaName + ")");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get checkpoint manager for specific app
     */
    public AppCheckpointManager getAppManager(String appId) {
        return appManagers.computeIfAbsent(appId, id -> {
            String schemaName = getSchemaForApp(id);
            return new AppCheckpointManager(id, schemaName);
        });
    }

    /**
     * Create checkpoint for specific app
     */
    public void createCheckpoint(String appId, String userId, String checkpointName) {
        AppCheckpointManager manager = getAppManager(appId);
        manager.createCheckpoint(userId, checkpointName);
    }

    /**
     * Undo for specific app and user
     */
    public void undo(String appId, String userId) {
        AppCheckpointManager manager = getAppManager(appId);
        manager.undo(userId);
    }

    /**
     * Redo for specific app and user
     */
    public void redo(String appId, String userId) {
        AppCheckpointManager manager = getAppManager(appId);
        manager.redo(userId);
    }

    private String getSchemaForApp(String appId) {
        String sql = "SELECT schema_name FROM checkpoint_system.apps WHERE app_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("schema_name");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException("App not found: " + appId);
    }
}
```

---

## Usage Example

### Setup Multiple Apps

```java
public class PlatformSetup {

    public static void main(String[] args) {
        MultiAppCheckpointSystem system = new MultiAppCheckpointSystem();

        // Register apps
        system.registerApp("crm_app", "CRM Application", "app_crm");
        system.registerApp("inventory_app", "Inventory System", "app_inventory");
        system.registerApp("hr_app", "HR Management", "app_hr");

        // Start event capture for each app
        AppCheckpointManager crmManager = system.getAppManager("crm_app");
        crmManager.startEventCapture("localhost", 3306, "user", "pass",
                                    "mysql-bin.000001", 4);

        AppCheckpointManager inventoryManager = system.getAppManager("inventory_app");
        inventoryManager.startEventCapture("localhost", 3306, "user", "pass",
                                          "mysql-bin.000001", 4);

        AppCheckpointManager hrManager = system.getAppManager("hr_app");
        hrManager.startEventCapture("localhost", 3306, "user", "pass",
                                   "mysql-bin.000001", 4);

        System.out.println("✅ All apps registered and listening to binlog");
    }
}
```

### Using Checkpoints Per App

```java
public class AppUsageExample {

    public static void main(String[] args) throws Exception {
        MultiAppCheckpointSystem system = new MultiAppCheckpointSystem();

        // User working on CRM app
        String userId = "user123";
        String appId = "crm_app";

        // Create initial checkpoint
        system.createCheckpoint(appId, userId, "initial");

        // User makes changes to CRM app
        executeUpdate("INSERT INTO app_crm.customers VALUES (1, 'Alice', 'alice@x.com')");
        executeUpdate("INSERT INTO app_crm.orders VALUES (1, 1, 99.99)");

        // Create checkpoint after changes
        system.createCheckpoint(appId, userId, "after_customer_add");

        // Meanwhile, another user working on Inventory app
        String userId2 = "user456";
        String appId2 = "inventory_app";

        system.createCheckpoint(appId2, userId2, "inventory_initial");
        executeUpdate("INSERT INTO app_inventory.products VALUES (1, 'Laptop', 999.99)");
        system.createCheckpoint(appId2, userId2, "after_product_add");

        // Undo in CRM app (doesn't affect Inventory app)
        system.undo(appId, userId);
        // Only app_crm.* changes are undone

        // Undo in Inventory app (doesn't affect CRM app)
        system.undo(appId2, userId2);
        // Only app_inventory.* changes are undone

        System.out.println("✅ Each app has independent undo/redo!");
    }
}
```

---

## Event Flow with Schema Filtering

```
MySQL Binlog Events (All Schemas Mixed)
──────────────────────────────────────────────────────────

Event 1: TABLE_MAP
         database: app_crm, table: customers, tableId: 108
         → tableMapper.register(108, "app_crm", "customers")

Event 2: WRITE_ROWS, tableId: 108
         → Check: tableMapper.isFromSchema(108, "app_crm")? YES
         → ✅ Capture for crm_app

Event 3: TABLE_MAP
         database: app_inventory, table: products, tableId: 109
         → tableMapper.register(109, "app_inventory", "products")

Event 4: UPDATE_ROWS, tableId: 109
         → Check: tableMapper.isFromSchema(109, "app_crm")? NO
         → ❌ Ignore (different schema)

Event 5: TABLE_MAP
         database: app_crm, table: orders, tableId: 110
         → tableMapper.register(110, "app_crm", "orders")

Event 6: WRITE_ROWS, tableId: 110
         → Check: tableMapper.isFromSchema(110, "app_crm")? YES
         → ✅ Capture for crm_app

Result:
  crm_app captured: Event 2, Event 6
  inventory_app captured: Event 4
  Complete isolation! ✅
```

---

## Undo/Redo with Schema Filtering

### Undo Operation

```java
public void undo(String userId) throws Exception {
    // 1. Get current and previous checkpoints FOR THIS APP
    String sql = "SELECT * FROM checkpoint_system.checkpoints " +
                "WHERE app_id = ? AND user_id = ? " +
                "ORDER BY created_at DESC LIMIT 2";

    // Execute query...
    Checkpoint current = ...;
    Checkpoint previous = ...;

    // 2. Load events ONLY for this app's schema
    String eventSql = "SELECT * FROM checkpoint_system.checkpoint_events " +
                     "WHERE checkpoint_id = ? " +
                     "  AND schema_name = ? " +  // CRITICAL: Filter by schema
                     "ORDER BY event_sequence DESC";

    // Execute with: (current.getId(), schemaName)
    List<CheckpointEvent> events = ...;

    // 3. Generate reverse SQL for this schema only
    for (CheckpointEvent event : events) {
        String tableName = schemaName + "." + event.getTableName();
        String sql = generateUndoSql(event, tableName);
        execute(sql);
    }

    System.out.println("✅ Undo complete for app: " + appId);
}
```

**Key Point:** The `schema_name` column ensures we only restore changes for the specific app!

---

## Database Isolation Architecture

### Scenario: 3 Apps on Same MySQL Server

```
MySQL Server (binlog captures ALL changes)
│
├── Binlog Reader (Single shared listener)
│   ├── Reads mysql-bin.000001
│   └── Distributes events to app managers
│
├── CRM App Manager (filters schema: app_crm)
│   ├── Captures events: app_crm.*
│   └── Stores in checkpoint_events with schema_name='app_crm'
│
├── Inventory App Manager (filters schema: app_inventory)
│   ├── Captures events: app_inventory.*
│   └── Stores in checkpoint_events with schema_name='app_inventory'
│
└── HR App Manager (filters schema: app_hr)
    ├── Captures events: app_hr.*
    └── Stores in checkpoint_events with schema_name='app_hr'
```

### Storage Isolation

```
checkpoint_system.checkpoint_events table
┌──────────────┬─────────────────┬──────────────┬────────┬──────────┐
│checkpoint_id │ schema_name     │ table_name   │ type   │ data     │
├──────────────┼─────────────────┼──────────────┼────────┼──────────┤
│ 101          │ app_crm         │ customers    │ INSERT │ {...}    │ ← CRM
│ 101          │ app_crm         │ orders       │ INSERT │ {...}    │ ← CRM
│ 102          │ app_inventory   │ products     │ UPDATE │ {...}    │ ← Inventory
│ 103          │ app_hr          │ employees    │ INSERT │ {...}    │ ← HR
│ 104          │ app_crm         │ customers    │ UPDATE │ {...}    │ ← CRM
│ 105          │ app_inventory   │ stock        │ DELETE │ {...}    │ ← Inventory
└──────────────┴─────────────────┴──────────────┴────────┴──────────┘

Query for CRM app only:
  SELECT * FROM checkpoint_events WHERE schema_name = 'app_crm'
  → Returns rows 1, 2, 4 only

Query for Inventory app only:
  SELECT * FROM checkpoint_events WHERE schema_name = 'app_inventory'
  → Returns rows 3, 5 only
```

---

## Performance Optimization for Multi-App

### 1. Single Binlog Reader with Event Distribution

```java
public class SharedBinlogReader {

    private BinaryLogClient client;
    private Map<String, AppCheckpointManager> appManagers = new ConcurrentHashMap<>();
    private TableMapper tableMapper = new TableMapper();

    /**
     * Single binlog reader that distributes events to all apps
     */
    public void start(String host, int port, String user, String password) {
        client = new BinaryLogClient(host, port, user, password);

        client.registerEventListener(event -> {
            EventData data = event.getData();

            if (data instanceof TableMapEventData) {
                TableMapEventData tableMapData = (TableMapEventData) data;
                tableMapper.registerTable(tableMapData);

                // Distribute TABLE_MAP to all managers
                appManagers.values().forEach(manager ->
                    manager.handleTableMap(tableMapData));

            } else if (data instanceof WriteRowsEventData) {
                distributeEvent((WriteRowsEventData) data);

            } else if (data instanceof UpdateRowsEventData) {
                distributeEvent((UpdateRowsEventData) data);

            } else if (data instanceof DeleteRowsEventData) {
                distributeEvent((DeleteRowsEventData) data);
            }
        });

        new Thread(() -> {
            try {
                client.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void distributeEvent(WriteRowsEventData data) {
        long tableId = data.getTableId();
        TableMapper.TableMetadata table = tableMapper.getTable(tableId);

        if (table == null) return;

        // Find the app manager for this schema
        String schemaName = table.getSchemaName();
        AppCheckpointManager manager = findManagerForSchema(schemaName);

        if (manager != null) {
            manager.handleInsertEvent(data, table);
        }
    }

    private AppCheckpointManager findManagerForSchema(String schemaName) {
        for (AppCheckpointManager manager : appManagers.values()) {
            if (manager.getSchemaName().equals(schemaName)) {
                return manager;
            }
        }
        return null;
    }

    public void registerApp(String appId, AppCheckpointManager manager) {
        appManagers.put(appId, manager);
    }
}
```

**Benefits:**
- ✅ Single binlog connection (efficient)
- ✅ Automatic distribution to correct app
- ✅ No duplicate event reading

### 2. Schema-Based Indexing

```sql
-- Optimize queries by schema
CREATE INDEX idx_schema_checkpoint ON checkpoint_events(schema_name, checkpoint_id);
CREATE INDEX idx_schema_seq ON checkpoint_events(schema_name, event_sequence);

-- Fast lookup for app-specific events
SELECT * FROM checkpoint_events
WHERE schema_name = 'app_crm'
  AND checkpoint_id = 101
ORDER BY event_sequence;
-- Uses idx_schema_checkpoint index
```

### 3. Partitioning by Schema (Optional)

```sql
-- For very large deployments with many apps
ALTER TABLE checkpoint_events
PARTITION BY HASH(CAST(CRC32(schema_name) AS SIGNED))
PARTITIONS 16;

-- Each partition holds subset of schemas
-- Improves query performance for specific schemas
```

---

## MySQL Configuration for Multi-Schema

### Server Configuration

```ini
[mysqld]
# Enable binary logging
log-bin = mysql-bin
server-id = 1

# CRITICAL: ROW format with FULL image
binlog_format = ROW
binlog_row_image = FULL

# Binlog retention
expire_logs_days = 7

# DO NOT use binlog-do-db or binlog-ignore-db
# Let application-level filtering handle schema isolation
# This ensures flexibility and doesn't lose any data
```

**Important:** Don't use `--binlog-do-db` or `--binlog-ignore-db` because:
1. Less flexible - requires server restart to add new apps
2. Statement-based filtering issues with `USE` statements
3. Application-level filtering is more precise and flexible

---

## Security and Permissions

### Per-App Database Users

```sql
-- Create user for CRM app
CREATE USER 'crm_app_user'@'%' IDENTIFIED BY 'crm_password';
GRANT ALL ON app_crm.* TO 'crm_app_user'@'%';
GRANT SELECT ON checkpoint_system.* TO 'crm_app_user'@'%';

-- Create user for Inventory app
CREATE USER 'inventory_app_user'@'%' IDENTIFIED BY 'inventory_password';
GRANT ALL ON app_inventory.* TO 'inventory_app_user'@'%';
GRANT SELECT ON checkpoint_system.* TO 'inventory_app_user'@'%';

-- Create user for HR app
CREATE USER 'hr_app_user'@'%' IDENTIFIED BY 'hr_password';
GRANT ALL ON app_hr.* TO 'hr_app_user'@'%';
GRANT SELECT ON checkpoint_system.* TO 'hr_app_user'@'%';

-- Shared user for binlog reading (checkpoint system)
CREATE USER 'checkpoint_system'@'%' IDENTIFIED BY 'checkpoint_password';
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_system'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_system'@'%';
GRANT ALL ON checkpoint_system.* TO 'checkpoint_system'@'%';

FLUSH PRIVILEGES;
```

---

## Complete Example: Low-Code Platform Workflow

### 1. Platform Initialization

```java
public class LowCodePlatform {

    public static void main(String[] args) {
        MultiAppCheckpointSystem system = new MultiAppCheckpointSystem();

        // Initialize shared binlog reader
        SharedBinlogReader binlogReader = new SharedBinlogReader();
        binlogReader.start("localhost", 3306, "checkpoint_system", "password");

        // Register apps
        system.registerApp("crm_app", "CRM", "app_crm");
        system.registerApp("inventory_app", "Inventory", "app_inventory");

        // Register app managers with binlog reader
        AppCheckpointManager crmManager = system.getAppManager("crm_app");
        AppCheckpointManager invManager = system.getAppManager("inventory_app");

        binlogReader.registerApp("crm_app", crmManager);
        binlogReader.registerApp("inventory_app", invManager);

        System.out.println("✅ Platform initialized with 2 apps");
    }
}
```

### 2. User Creates Form in CRM App

```java
// User "alice" working on CRM app
String appId = "crm_app";
String userId = "alice";

// Create initial checkpoint
system.createCheckpoint(appId, userId, "form_initial");

// User creates form field
Connection conn = getConnection("app_crm");
PreparedStatement stmt = conn.prepareStatement(
    "INSERT INTO app_crm.form_fields (field_name, properties) " +
    "VALUES (?, ?)"
);
stmt.setString(1, "email");
stmt.setString(2, "{\"color\":\"blue\",\"size\":\"medium\"}");
stmt.executeUpdate();

// Binlog event captured:
// Schema: app_crm ✅
// Table: form_fields
// Event: INSERT
// Data: {field_name: "email", properties: {...}}

// Create checkpoint after change
system.createCheckpoint(appId, userId, "email_field_added");
```

### 3. Another User Works on Inventory App (Simultaneously)

```java
// User "bob" working on Inventory app
String appId2 = "inventory_app";
String userId2 = "bob";

// Create checkpoint
system.createCheckpoint(appId2, userId2, "inventory_start");

// Bob adds product
Connection conn2 = getConnection("app_inventory");
PreparedStatement stmt2 = conn2.prepareStatement(
    "INSERT INTO app_inventory.products (name, price) VALUES (?, ?)"
);
stmt2.setString(1, "Laptop");
stmt2.setDouble(2, 999.99);
stmt2.executeUpdate();

// Binlog event captured:
// Schema: app_inventory ✅
// Table: products
// Event: INSERT
// Data: {name: "Laptop", price: 999.99}

// Create checkpoint
system.createCheckpoint(appId2, userId2, "laptop_added");
```

### 4. Independent Undo/Redo

```java
// Alice undoes in CRM app
system.undo("crm_app", "alice");
// Only affects app_crm schema
// Query: SELECT * FROM checkpoint_events WHERE schema_name = 'app_crm'
// Result: Removes email field from app_crm.form_fields

// Bob's inventory data is UNAFFECTED!
// Query: SELECT * FROM app_inventory.products
// Result: Laptop still exists ✅

// Bob undoes in Inventory app
system.undo("inventory_app", "bob");
// Only affects app_inventory schema
// Result: Removes laptop from app_inventory.products

// Alice's CRM data is UNAFFECTED!
// Result: email field state unchanged ✅

System.out.println("✅ Complete isolation between apps!");
```

---

## Schema Naming Convention

### Recommended Pattern

```
app_{app_identifier}

Examples:
  app_crm
  app_inventory
  app_hr
  app_project_management
  app_customer_portal
```

**Benefits:**
- Clear identification of app schemas
- Easy to filter in queries
- Consistent naming across platform

### Schema Registry

```java
public class SchemaRegistry {

    /**
     * Generate schema name for app
     */
    public static String generateSchemaName(String appIdentifier) {
        // Sanitize app identifier
        String sanitized = appIdentifier
            .toLowerCase()
            .replaceAll("[^a-z0-9_]", "_");

        return "app_" + sanitized;
    }

    /**
     * Create schema for new app
     */
    public static void createAppSchema(String appId, String appName) {
        String schemaName = generateSchemaName(appId);

        // Create schema
        executeUpdate("CREATE DATABASE IF NOT EXISTS " + schemaName);

        // Register in app registry
        String sql = "INSERT INTO checkpoint_system.apps " +
                    "(app_id, app_name, schema_name) VALUES (?, ?, ?)";
        // Execute...

        System.out.println("✅ Created schema: " + schemaName + " for app: " + appName);
    }
}
```

---

## Advantages of Multi-Schema Architecture

✅ **Complete isolation** - Each app's data and checkpoints are separate
✅ **Scalability** - Add new apps without affecting existing ones
✅ **Security** - Per-app database users and permissions
✅ **Performance** - Filter binlog events by schema efficiently
✅ **Flexibility** - Each app can have different table structures
✅ **Multi-tenancy** - Natural separation for multi-tenant platforms
✅ **Backup/restore** - Can backup/restore individual app schemas
✅ **Testing** - Can test one app without affecting others

---

## Next Steps

1. **Implement SharedBinlogReader** - Single binlog reader for all apps
2. **Add schema filtering** - Filter events by schema_name in event handlers
3. **Create app registry** - Track all apps and their schemas
4. **Build per-app managers** - AppCheckpointManager for each app
5. **Test isolation** - Verify undo/redo works independently per app
6. **Add CLI tool** - Command-line interface for multi-app management

Should we proceed with implementation? 🚀

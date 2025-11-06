# MySQL Binlog ROW Format with FULL Image - Complete Undo/Redo/Versioning Guide

## Executive Summary

With `binlog_format = ROW` and `binlog_row_image = FULL`, MySQL binary logs capture **complete before and after states** of every row change. This is the **golden ticket** for implementing undo/redo/versioning systems because we have:

- ✅ **Complete row data** before and after each change
- ✅ **No SQL parsing needed** - data is already structured
- ✅ **Perfect for reversibility** - before image = what to restore
- ✅ **Automatic capture** - MySQL does the work for us

---

## Understanding the Configuration

### binlog_format = ROW

**What it means:**
- MySQL logs **actual row changes**, not SQL statements
- Each INSERT/UPDATE/DELETE is recorded as row-level events
- Events contain the actual data, not "UPDATE users SET name='John'"

**Comparison:**

| Format | What's Logged | Use Case |
|--------|---------------|----------|
| STATEMENT | SQL: `UPDATE users SET name='John' WHERE id=1` | Compact, but non-deterministic |
| MIXED | SQL or ROW (MySQL decides) | Balanced approach |
| **ROW** | **Row data: {id:1, name:'Jane'} → {id:1, name:'John'}** | **Exact replication, undo/redo** |

### binlog_row_image = FULL

**What it means:**
- Logs **ALL columns** in both before and after images
- Even unchanged columns are included
- Maximum information, maximum reversibility

**Options:**

| Setting | Before Image (BI) | After Image (AI) | Use Case |
|---------|-------------------|------------------|----------|
| **FULL** | **All columns** | **All columns** | **Undo/redo, complete history** |
| MINIMAL | PK + changed columns | Changed columns only | Save space, replication |
| NOBLOB | All except BLOBs | All except BLOBs | Balance space/functionality |

**Why FULL is critical for undo/redo:**

```
UPDATE users SET email='new@email.com' WHERE id=1;

With FULL:
Before: {id:1, name:'Alice', email:'old@email.com', status:'active'}
After:  {id:1, name:'Alice', email:'new@email.com', status:'active'}
→ We can restore EXACT previous state

With MINIMAL:
Before: {id:1, email:'old@email.com'}  ← Missing name, status!
After:  {email:'new@email.com'}
→ Cannot fully restore previous state
```

---

## Binary Log Event Structure in ROW Format

### Event Types

```
WriteRowsEvent     → INSERT operations
UpdateRowsEvent    → UPDATE operations
DeleteRowsEvent    → DELETE operations
```

### Event Data Structure

#### 1. WriteRowsEvent (INSERT)

```java
WriteRowsEventData {
    long tableId;              // Which table
    BitSet includedColumns;    // Which columns are present
    List<Serializable[]> rows; // The inserted rows
}

Example:
INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@x.com');

Event data:
tableId: 108
includedColumns: [0, 1, 2]  // All 3 columns
rows: [
    [1, "Alice", "alice@x.com"]
]
```

#### 2. UpdateRowsEvent (UPDATE)

```java
UpdateRowsEventData {
    long tableId;
    BitSet includedColumns;
    BitSet includedColumnsBeforeUpdate;
    List<Map.Entry<Serializable[], Serializable[]>> rows;
    // Each entry: Key = BEFORE image, Value = AFTER image
}

Example:
UPDATE users SET email='alice.smith@x.com' WHERE id=1;

Event data:
tableId: 108
rows: [
    Entry {
        key:   [1, "Alice", "alice@x.com"]           ← BEFORE
        value: [1, "Alice", "alice.smith@x.com"]     ← AFTER
    }
]
```

**This is PERFECT for undo/redo!**
- **Undo:** Use the BEFORE image (key)
- **Redo:** Use the AFTER image (value)

#### 3. DeleteRowsEvent (DELETE)

```java
DeleteRowsEventData {
    long tableId;
    BitSet includedColumns;
    List<Serializable[]> rows;  // The deleted rows
}

Example:
DELETE FROM users WHERE id=1;

Event data:
tableId: 108
rows: [
    [1, "Alice", "alice.smith@x.com"]  ← Complete row that was deleted
]
```

**Perfect for undo:**
- **Undo:** Re-INSERT this exact row
- **Redo:** DELETE it again

---

## Architecture: Checkpoint System Using ROW + FULL

### Database Schema

```sql
-- Checkpoint metadata
CREATE TABLE checkpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Binlog position
    binlog_filename VARCHAR(255) NOT NULL,
    binlog_position BIGINT NOT NULL,
    gtid_set TEXT,

    -- Navigation
    parent_checkpoint_id BIGINT,

    INDEX idx_user_time (user_id, created_at),
    INDEX idx_binlog (binlog_filename, binlog_position),
    FOREIGN KEY (parent_checkpoint_id) REFERENCES checkpoints(id)
);

-- Captured events (for offline replay and binlog purge protection)
CREATE TABLE checkpoint_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT NOT NULL,

    -- Event metadata
    event_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    binlog_position BIGINT NOT NULL,

    -- Event data (from ROW format with FULL image)
    before_image JSON,  -- BEFORE state (for UPDATE/DELETE)
    after_image JSON,   -- AFTER state (for INSERT/UPDATE)

    -- For ordering
    event_sequence INT NOT NULL,

    INDEX idx_checkpoint (checkpoint_id, event_sequence),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
);

-- User's current position
CREATE TABLE user_checkpoint_position (
    user_id VARCHAR(255) PRIMARY KEY,
    current_checkpoint_id BIGINT NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (current_checkpoint_id) REFERENCES checkpoints(id)
);

-- Version history (optional - for showing change log)
CREATE TABLE version_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    checkpoint_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    row_identifier VARCHAR(255) NOT NULL,  -- e.g., "users:1"
    change_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    before_data JSON,
    after_data JSON,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_table (user_id, table_name),
    INDEX idx_checkpoint (checkpoint_id),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id)
);
```

---

## Operation Flows

### 1. Checkpoint Creation with Event Capture

```
User calls: createCheckpoint("form_initial")
                    │
                    ▼
┌────────────────────────────────────────────────────┐
│ 1. Get Current Binlog Position                    │
│    ─────────────────────────────────────────────   │
│    SHOW MASTER STATUS;                             │
│    → File: mysql-bin.000001                        │
│    → Position: 5432                                │
└────────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────────┐
│ 2. Create Checkpoint Record                       │
│    ─────────────────────────────────────────────   │
│    INSERT INTO checkpoints (                       │
│      user_id: "user123",                          │
│      name: "form_initial",                        │
│      binlog_filename: "mysql-bin.000001",         │
│      binlog_position: 5432,                       │
│      parent_checkpoint_id: 42  -- previous CP     │
│    )                                              │
│    → Returns checkpoint_id: 43                    │
└────────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────────┐
│ 3. Start Event Capture (Async Background)         │
│    ─────────────────────────────────────────────   │
│    BinaryLogClient client = new BinaryLogClient() │
│    client.setBinlogPosition(5432)                 │
│    client.registerEventListener(event -> {        │
│      // Capture events until next checkpoint      │
│      storeEvent(checkpoint_id: 43, event)         │
│    })                                             │
│    client.connect()                               │
└────────────────────────────────────────────────────┘
                    │
                    ▼
            ✅ Checkpoint created!
        (Storage: ~100 bytes metadata)
        (Events captured in background)
```

### 2. Event Storage from ROW Format

```
Binlog Event Received
        │
        ▼
┌─────────────────────────────────────────────┐
│ Determine Event Type                        │
└─────────────────────────────────────────────┘
        │
        ├──────────────┬────────────────┬──────────────┐
        ▼              ▼                ▼              ▼
   INSERT          UPDATE           DELETE       (ignore)
        │              │                │
        ▼              ▼                ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│ WriteRows    │ │ UpdateRows  │ │ DeleteRows  │
│ EventData    │ │ EventData   │ │ EventData   │
└──────────────┘ └─────────────┘ └─────────────┘
        │              │                │
        ▼              ▼                ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│ Extract:     │ │ Extract:    │ │ Extract:    │
│ - rows       │ │ - before[]  │ │ - rows      │
│              │ │ - after[]   │ │             │
└──────────────┘ └─────────────┘ └─────────────┘
        │              │                │
        ▼              ▼                ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│ Store:       │ │ Store:      │ │ Store:      │
│ before: null │ │ before: {..}│ │ before: {..}│
│ after: {...} │ │ after: {..} │ │ after: null │
└──────────────┘ └─────────────┘ └─────────────┘
        │              │                │
        └──────────────┴────────────────┘
                       │
                       ▼
        INSERT INTO checkpoint_events (
          checkpoint_id,
          event_type,
          table_name,
          before_image,  -- JSON
          after_image,   -- JSON
          event_sequence
        )
```

### 3. Undo Operation - Leveraging BEFORE Images

```
User calls: undo()
        │
        ▼
┌────────────────────────────────────────────┐
│ 1. Get Current & Previous Checkpoints      │
│    Current: CP43 @ pos 6789                │
│    Previous: CP42 @ pos 5432               │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 2. Load Captured Events (in reverse order)│
│    SELECT * FROM checkpoint_events         │
│    WHERE checkpoint_id = 43                │
│    ORDER BY event_sequence DESC            │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 3. Generate REVERSE Operations            │
│    For each event (in reverse order):     │
└────────────────────────────────────────────┘
        │
        ├──────────────┬────────────────┬──────────────┐
        │              │                │              │
  Event: INSERT   Event: UPDATE   Event: DELETE
  after_image     before_image    before_image
        │              │                │
        ▼              ▼                ▼
    DELETE         UPDATE           INSERT
        │              │                │
        ▼              ▼                ▼

Event 1: INSERT users {id:5, name:"Bob", email:"bob@x.com"}
Reverse: DELETE FROM users WHERE id=5

Event 2: UPDATE form_fields
         BEFORE: {id:2, props:'{"color":"blue"}'}
         AFTER:  {id:2, props:'{"color":"red"}'}
Reverse: UPDATE form_fields
         SET props='{"color":"blue"}' WHERE id=2

Event 3: DELETE orders {id:10, total:99.99}
Reverse: INSERT INTO orders
         VALUES (10, 99.99, ...)

        │
        ▼
┌────────────────────────────────────────────┐
│ 4. Execute Reverse SQL in Transaction     │
│    BEGIN;                                  │
│    DELETE FROM users WHERE id=5;           │
│    UPDATE form_fields                      │
│      SET props='{"color":"blue"}'...;      │
│    INSERT INTO orders VALUES(10, 99.99...);│
│    COMMIT;                                 │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 5. Update User Position to CP42           │
└────────────────────────────────────────────┘
        │
        ▼
    ✅ Undo complete!
```

**Key Insight:** With `binlog_row_image=FULL`, the BEFORE image contains **all columns**, so we can perfectly restore the previous state!

### 4. Redo Operation - Leveraging AFTER Images

```
User calls: redo()
        │
        ▼
┌────────────────────────────────────────────┐
│ 1. Get Current & Next Checkpoints          │
│    Current: CP42 @ pos 5432                │
│    Next: CP43 @ pos 6789                   │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 2. Load Captured Events (forward order)   │
│    SELECT * FROM checkpoint_events         │
│    WHERE checkpoint_id = 43                │
│    ORDER BY event_sequence ASC             │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 3. Generate FORWARD Operations            │
│    For each event (in forward order):     │
└────────────────────────────────────────────┘
        │
        ├──────────────┬────────────────┬──────────────┐
        │              │                │              │
  Event: INSERT   Event: UPDATE   Event: DELETE
  after_image     after_image     before_image
        │              │                │
        ▼              ▼                ▼
    INSERT         UPDATE           DELETE
        │              │                │
        ▼              ▼                ▼

Event 1: INSERT users {id:5, name:"Bob", email:"bob@x.com"}
Forward: INSERT INTO users
         VALUES (5, "Bob", "bob@x.com")

Event 2: UPDATE form_fields
         AFTER: {id:2, props:'{"color":"red"}'}
Forward: UPDATE form_fields
         SET props='{"color":"red"}' WHERE id=2

Event 3: DELETE orders {id:10}
Forward: DELETE FROM orders WHERE id=10

        │
        ▼
┌────────────────────────────────────────────┐
│ 4. Execute Forward SQL in Transaction     │
│    BEGIN;                                  │
│    INSERT INTO users VALUES(5,...);        │
│    UPDATE form_fields SET...;              │
│    DELETE FROM orders WHERE id=10;         │
│    COMMIT;                                 │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 5. Update User Position to CP43           │
└────────────────────────────────────────────┘
        │
        ▼
    ✅ Redo complete!
```

### 5. Revert to Specific Checkpoint

```
User calls: revertTo("checkpoint_name")
        │
        ▼
┌────────────────────────────────────────────┐
│ 1. Find Target Checkpoint                 │
│    SELECT * FROM checkpoints               │
│    WHERE name = "checkpoint_name"          │
│      AND user_id = "user123"               │
│    → Target: CP40                          │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 2. Get Current Position                    │
│    Current: CP43                           │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 3. Determine Direction                     │
│    CP43 → CP40 = BACKWARD (undo)           │
│    Need to undo: CP43, CP42, CP41          │
└────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 4. Execute Multiple Undo Operations        │
│    undo() from CP43 to CP42                │
│    undo() from CP42 to CP41                │
│    undo() from CP41 to CP40                │
└────────────────────────────────────────────┘
        │
        ▼
    ✅ Reverted to CP40!

Alternative: If CP43 → CP50 = FORWARD (redo)
             Execute multiple redo operations
```

---

## Java Implementation

### 1. Event Listener with ROW Format Parsing

```java
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.google.gson.Gson;

public class CheckpointEventCapture {

    private BinaryLogClient client;
    private Gson gson = new Gson();
    private long currentCheckpointId;
    private int eventSequence = 0;

    public void startCapture(String host, int port, String user, String password,
                            String binlogFilename, long binlogPosition) {

        client = new BinaryLogClient(host, port, user, password);
        client.setBinlogFilename(binlogFilename);
        client.setBinlogPosition(binlogPosition);

        client.registerEventListener(event -> {
            EventData data = event.getData();

            if (data instanceof TableMapEventData) {
                // Track table metadata (tableId -> tableName mapping)
                handleTableMapEvent((TableMapEventData) data);

            } else if (data instanceof WriteRowsEventData) {
                // INSERT operation
                handleInsertEvent((WriteRowsEventData) data);

            } else if (data instanceof UpdateRowsEventData) {
                // UPDATE operation - has BEFORE and AFTER images!
                handleUpdateEvent((UpdateRowsEventData) data);

            } else if (data instanceof DeleteRowsEventData) {
                // DELETE operation
                handleDeleteEvent((DeleteRowsEventData) data);
            }
        });

        // Start listening (async)
        new Thread(() -> {
            try {
                client.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleInsertEvent(WriteRowsEventData data) {
        String tableName = getTableName(data.getTableId());
        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows) {
            // Convert row to JSON
            String afterImage = serializeRow(tableName, row);

            // Store event
            storeEvent(
                currentCheckpointId,
                "INSERT",
                tableName,
                null,           // No before image for INSERT
                afterImage,     // After image = inserted data
                eventSequence++
            );
        }
    }

    private void handleUpdateEvent(UpdateRowsEventData data) {
        String tableName = getTableName(data.getTableId());
        List<Map.Entry<Serializable[], Serializable[]>> rows = data.getRows();

        for (Map.Entry<Serializable[], Serializable[]> row : rows) {
            Serializable[] beforeRow = row.getKey();   // BEFORE image
            Serializable[] afterRow = row.getValue();   // AFTER image

            // Convert both to JSON
            String beforeImage = serializeRow(tableName, beforeRow);
            String afterImage = serializeRow(tableName, afterRow);

            // Store event with BOTH images
            storeEvent(
                currentCheckpointId,
                "UPDATE",
                tableName,
                beforeImage,    // Complete BEFORE state (thanks to FULL)
                afterImage,     // Complete AFTER state
                eventSequence++
            );
        }
    }

    private void handleDeleteEvent(DeleteRowsEventData data) {
        String tableName = getTableName(data.getTableId());
        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows) {
            // Convert row to JSON
            String beforeImage = serializeRow(tableName, row);

            // Store event
            storeEvent(
                currentCheckpointId,
                "DELETE",
                tableName,
                beforeImage,    // Before image = deleted data
                null,           // No after image for DELETE
                eventSequence++
            );
        }
    }

    private String serializeRow(String tableName, Serializable[] row) {
        // Get column names for this table
        List<String> columns = getColumnNames(tableName);

        // Build JSON object
        Map<String, Object> rowData = new HashMap<>();
        for (int i = 0; i < columns.size() && i < row.length; i++) {
            rowData.put(columns.get(i), row[i]);
        }

        return gson.toJson(rowData);
    }

    private void storeEvent(long checkpointId, String eventType,
                           String tableName, String beforeImage,
                           String afterImage, int sequence) {
        String sql = "INSERT INTO checkpoint_events " +
                    "(checkpoint_id, event_type, table_name, " +
                    "before_image, after_image, event_sequence, " +
                    "timestamp, binlog_position) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, checkpointId);
            stmt.setString(2, eventType);
            stmt.setString(3, tableName);
            stmt.setString(4, beforeImage);
            stmt.setString(5, afterImage);
            stmt.setInt(6, sequence);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.setLong(8, getCurrentBinlogPosition());

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```

### 2. Undo Operation - Using BEFORE Images

```java
public class CheckpointManager {

    public void undo(String userId) throws Exception {
        // 1. Get checkpoints
        Checkpoint current = getCurrentCheckpoint(userId);
        Checkpoint previous = getPreviousCheckpoint(current);

        if (previous == null) {
            throw new IllegalStateException("Already at first checkpoint");
        }

        // 2. Load events for current checkpoint (in REVERSE order)
        List<CheckpointEvent> events = loadEvents(current.getId(), true);

        // 3. Generate and execute reverse operations
        executeUndo(events);

        // 4. Update user position
        updateUserPosition(userId, previous.getId());

        System.out.println("✅ Undo complete: " +
                          current.getName() + " → " + previous.getName());
    }

    private void executeUndo(List<CheckpointEvent> events) throws Exception {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);

        try {
            for (CheckpointEvent event : events) {
                String sql = generateUndoSql(event);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    setParameters(stmt, event);
                    stmt.executeUpdate();
                }
            }

            conn.commit();

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    private String generateUndoSql(CheckpointEvent event) {
        switch (event.getEventType()) {
            case "INSERT":
                // Reverse: DELETE the inserted row
                return generateDeleteSql(event);

            case "UPDATE":
                // Reverse: UPDATE with BEFORE image
                return generateUpdateSql(event, event.getBeforeImage());

            case "DELETE":
                // Reverse: INSERT the deleted row back
                return generateInsertSql(event);

            default:
                throw new IllegalArgumentException("Unknown event type: " +
                                                  event.getEventType());
        }
    }

    private String generateDeleteSql(CheckpointEvent event) {
        // event.afterImage contains the inserted data
        JsonObject data = JsonParser.parseString(event.getAfterImage())
                                    .getAsJsonObject();

        // Build WHERE clause using primary key
        String pkColumn = getPrimaryKeyColumn(event.getTableName());
        Object pkValue = data.get(pkColumn).getAsString();

        return String.format("DELETE FROM %s WHERE %s = ?",
                           event.getTableName(), pkColumn);
    }

    private String generateUpdateSql(CheckpointEvent event, String imageJson) {
        // Parse the BEFORE image (contains ALL columns thanks to FULL)
        JsonObject data = JsonParser.parseString(imageJson).getAsJsonObject();

        // Build SET clause for all columns
        List<String> setClauses = new ArrayList<>();
        for (String column : data.keySet()) {
            if (!isPrimaryKey(event.getTableName(), column)) {
                setClauses.add(column + " = ?");
            }
        }

        String pkColumn = getPrimaryKeyColumn(event.getTableName());

        return String.format("UPDATE %s SET %s WHERE %s = ?",
                           event.getTableName(),
                           String.join(", ", setClauses),
                           pkColumn);
    }

    private String generateInsertSql(CheckpointEvent event) {
        // event.beforeImage contains the complete deleted row (thanks to FULL)
        JsonObject data = JsonParser.parseString(event.getBeforeImage())
                                    .getAsJsonObject();

        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        for (String column : data.keySet()) {
            columns.add(column);
            placeholders.add("?");
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)",
                           event.getTableName(),
                           String.join(", ", columns),
                           String.join(", ", placeholders));
    }
}
```

### 3. Redo Operation - Using AFTER Images

```java
public void redo(String userId) throws Exception {
    // 1. Get checkpoints
    Checkpoint current = getCurrentCheckpoint(userId);
    Checkpoint next = getNextCheckpoint(current);

    if (next == null) {
        throw new IllegalStateException("Already at latest checkpoint");
    }

    // 2. Load events for next checkpoint (in FORWARD order)
    List<CheckpointEvent> events = loadEvents(next.getId(), false);

    // 3. Generate and execute forward operations
    executeRedo(events);

    // 4. Update user position
    updateUserPosition(userId, next.getId());

    System.out.println("✅ Redo complete: " +
                      current.getName() + " → " + next.getName());
}

private void executeRedo(List<CheckpointEvent> events) throws Exception {
    Connection conn = dataSource.getConnection();
    conn.setAutoCommit(false);

    try {
        for (CheckpointEvent event : events) {
            String sql = generateRedoSql(event);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setParameters(stmt, event);
                stmt.executeUpdate();
            }
        }

        conn.commit();

    } catch (Exception e) {
        conn.rollback();
        throw e;
    } finally {
        conn.setAutoCommit(true);
        conn.close();
    }
}

private String generateRedoSql(CheckpointEvent event) {
    switch (event.getEventType()) {
        case "INSERT":
            // Forward: INSERT with AFTER image
            return generateInsertSql(event);

        case "UPDATE":
            // Forward: UPDATE with AFTER image
            return generateUpdateSql(event, event.getAfterImage());

        case "DELETE":
            // Forward: DELETE
            return generateDeleteSql(event);

        default:
            throw new IllegalArgumentException("Unknown event type: " +
                                              event.getEventType());
    }
}
```

---

## Versioning System

### Complete Change History

```java
public class VersionHistoryManager {

    public List<VersionEntry> getVersionHistory(String userId,
                                                String tableName,
                                                String rowId) {
        String sql = "SELECT vh.* FROM version_history vh " +
                    "JOIN checkpoints cp ON vh.checkpoint_id = cp.id " +
                    "WHERE vh.user_id = ? " +
                    "  AND vh.table_name = ? " +
                    "  AND vh.row_identifier = ? " +
                    "ORDER BY vh.changed_at DESC";

        // Returns list of all changes to this row
        // Each entry shows: what changed, when, which checkpoint
    }

    public void showVersionTimeline(String userId, String table, String rowId) {
        List<VersionEntry> versions = getVersionHistory(userId, table, rowId);

        System.out.println("Version History for " + table + ":" + rowId);
        System.out.println("─".repeat(60));

        for (int i = 0; i < versions.size(); i++) {
            VersionEntry v = versions.get(i);

            System.out.printf("Version %d - %s (Checkpoint: %s)\n",
                            versions.size() - i,
                            v.getChangedAt(),
                            v.getCheckpointName());

            System.out.println("  Change: " + v.getChangeType());

            if (v.getBeforeData() != null) {
                System.out.println("  Before: " +
                                 formatJson(v.getBeforeData()));
            }

            if (v.getAfterData() != null) {
                System.out.println("  After:  " +
                                 formatJson(v.getAfterData()));
            }

            System.out.println();
        }
    }
}

// Example output:
// Version History for form_fields:2
// ────────────────────────────────────────────────────────────
// Version 3 - 2025-01-15 14:30:22 (Checkpoint: final_version)
//   Change: UPDATE
//   Before: {"id":2,"props":"{\"color\":\"blue\",\"size\":\"large\"}"}
//   After:  {"id":2,"props":"{\"color\":\"red\",\"size\":\"large\"}"}
//
// Version 2 - 2025-01-15 14:25:10 (Checkpoint: size_change)
//   Change: UPDATE
//   Before: {"id":2,"props":"{\"color\":\"blue\",\"size\":\"medium\"}"}
//   After:  {"id":2,"props":"{\"color\":\"blue\",\"size\":\"large\"}"}
//
// Version 1 - 2025-01-15 14:20:00 (Checkpoint: initial)
//   Change: INSERT
//   After:  {"id":2,"props":"{\"color\":\"blue\",\"size\":\"medium\"}"}
```

---

## Performance Optimization

### 1. Event Capture Strategy

**Problem:** Capturing ALL events can generate massive data

**Solutions:**

#### Option A: Time-Windowed Capture
```java
// Only capture events between checkpoints
public void createCheckpoint(String name, String userId) {
    // Stop previous capture
    stopEventCapture();

    // Record new checkpoint position
    long checkpointId = recordCheckpoint(name, userId);

    // Start new capture window
    startEventCapture(checkpointId);
}
```

#### Option B: Selective Table Capture
```java
// Only capture specific tables
Set<String> monitoredTables = Set.of("form_fields", "users", "orders");

private void handleUpdateEvent(UpdateRowsEventData data) {
    String tableName = getTableName(data.getTableId());

    // Only capture if table is monitored
    if (!monitoredTables.contains(tableName)) {
        return;
    }

    // Store event...
}
```

#### Option C: Lazy Capture on Demand
```java
// Don't capture events by default
// Only capture when user creates checkpoint

public void createCheckpoint(String name, String userId,
                            boolean captureEvents) {
    if (captureEvents) {
        // Read binlog from last checkpoint to current
        captureHistoricalEvents(lastCheckpointId, currentPosition);
    }

    // Record checkpoint...
}
```

### 2. Event Storage Optimization

**Compress JSON data:**
```java
private String compressJson(String json) {
    // Use GZIP compression for large JSON objects
    byte[] compressed = gzip(json.getBytes());
    return Base64.getEncoder().encodeToString(compressed);
}

// Store compressed in database
stmt.setString(4, compressJson(beforeImage));
```

**Delta compression for UPDATEs:**
```java
// For UPDATE events, store only changed columns
private String createDelta(String beforeJson, String afterJson) {
    JsonObject before = JsonParser.parseString(beforeJson).getAsJsonObject();
    JsonObject after = JsonParser.parseString(afterJson).getAsJsonObject();

    JsonObject delta = new JsonObject();

    for (String key : after.keySet()) {
        if (!before.get(key).equals(after.get(key))) {
            delta.add(key, after.get(key));  // Only store changed values
        }
    }

    return delta.toString();
}
```

### 3. Binlog Purge Protection

**Archive binlog files:**
```sql
-- Configure MySQL to keep binlogs longer
SET GLOBAL expire_logs_days = 30;
SET GLOBAL binlog_expire_logs_seconds = 2592000;  -- 30 days
```

**Checkpoint compaction:**
```java
// Periodically compact old checkpoints
public void compactCheckpoints(String userId, int keepRecent) {
    // Keep last N checkpoints with full events
    // For older checkpoints, delete events but keep metadata

    List<Checkpoint> oldCheckpoints = getCheckpointsOlderThan(userId, keepRecent);

    for (Checkpoint cp : oldCheckpoints) {
        // Delete events
        deleteEvents(cp.getId());

        // Mark checkpoint as "compacted"
        markAsCompacted(cp.getId());
    }
}
```

---

## Storage Analysis

### With binlog_row_image = FULL

**Example UPDATE:**
```sql
UPDATE users SET email = 'new@email.com' WHERE id = 1;
```

**Storage in checkpoint_events:**
```json
{
  "event_type": "UPDATE",
  "table_name": "users",
  "before_image": {
    "id": 1,
    "username": "alice",
    "email": "old@email.com",
    "status": "active",
    "created_at": "2025-01-01 10:00:00",
    "last_login": "2025-01-15 09:30:00"
  },
  "after_image": {
    "id": 1,
    "username": "alice",
    "email": "new@email.com",      // Only this changed
    "status": "active",
    "created_at": "2025-01-01 10:00:00",
    "last_login": "2025-01-15 09:30:00"
  }
}
```

**Size:** ~250 bytes per event (6 columns × 2 images)

**For 1000 tables with 100 changes between checkpoints:**
- 100 events × 250 bytes = 25 KB per checkpoint
- 100 checkpoints = 2.5 MB
- **Very manageable!**

**Compare to full snapshot:**
- 1000 tables × 10 KB avg = 10 MB per checkpoint
- 100 checkpoints = 1 GB
- **40x larger!**

---

## Why FULL is Worth It

### Storage Trade-off

**binlog_row_image = MINIMAL:**
```json
// UPDATE event - only changed columns
{
  "before": {"id": 1, "email": "old@email.com"},
  "after": {"email": "new@email.com"}
}
// ❌ Missing: username, status, created_at, last_login
// ❌ Cannot restore complete row state
```

**binlog_row_image = FULL:**
```json
// UPDATE event - ALL columns
{
  "before": {
    "id": 1, "username": "alice", "email": "old@email.com",
    "status": "active", "created_at": "...", "last_login": "..."
  },
  "after": {
    "id": 1, "username": "alice", "email": "new@email.com",
    "status": "active", "created_at": "...", "last_login": "..."
  }
}
// ✅ Complete state - perfect for undo/redo
```

**Storage increase:** ~2-3x more data
**Benefit:** Perfect state restoration, simple undo/redo logic

**Verdict:** For checkpoint/undo systems, **FULL is essential**

---

## Configuration Checklist

### MySQL Server Configuration (my.cnf or my.ini)

```ini
[mysqld]
# CRITICAL: Enable binary logging
log-bin = mysql-bin
server-id = 1

# CRITICAL: Use ROW format for data-level logging
binlog_format = ROW

# CRITICAL: Store complete before/after images
binlog_row_image = FULL

# Recommended: Keep binlogs for 30 days
expire_logs_days = 30
# OR (MySQL 8.0+)
binlog_expire_logs_seconds = 2592000

# Recommended: Rotate at 100MB
max_binlog_size = 100M

# Optional: Include original SQL in binlog comments
binlog_rows_query_log_events = ON
```

### Database User Permissions

```sql
-- Create dedicated user for checkpoint system
CREATE USER 'checkpoint_app'@'%' IDENTIFIED BY 'secure_password';

-- Grant replication privileges (required for binlog reading)
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_app'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_app'@'%';

-- Grant data access
GRANT SELECT, INSERT, UPDATE, DELETE ON myapp.* TO 'checkpoint_app'@'%';

-- Grant access to checkpoint tables
GRANT ALL ON myapp.checkpoints TO 'checkpoint_app'@'%';
GRANT ALL ON myapp.checkpoint_events TO 'checkpoint_app'@'%';
GRANT ALL ON myapp.user_checkpoint_position TO 'checkpoint_app'@'%';
GRANT ALL ON myapp.version_history TO 'checkpoint_app'@'%';

FLUSH PRIVILEGES;
```

### Verify Configuration

```sql
-- Check binlog is enabled
SHOW VARIABLES LIKE 'log_bin';
-- Expected: ON

-- Check format is ROW
SHOW VARIABLES LIKE 'binlog_format';
-- Expected: ROW

-- Check row image is FULL
SHOW VARIABLES LIKE 'binlog_row_image';
-- Expected: FULL

-- List binlog files
SHOW BINARY LOGS;

-- Check current position
SHOW MASTER STATUS;
```

---

## Complete Example: Form Field Editing with Undo/Redo

### Scenario

User edits form field properties with ability to undo/redo changes.

### Setup

```sql
CREATE TABLE form_fields (
    id INT PRIMARY KEY AUTO_INCREMENT,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    properties JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO form_fields (field_name, field_type, properties) VALUES
('email', 'text', '{"placeholder":"Enter email","color":"blue","size":"medium"}'),
('phone', 'tel', '{"placeholder":"Phone number","color":"gray","size":"small"}');
```

### User Actions

```java
CheckpointManager manager = new CheckpointManager(dataSource);

// Initial state
manager.createCheckpoint("initial", "user123");
// Binlog position: mysql-bin.000001 @ 1000

// User changes email field color to red
executeUpdate("UPDATE form_fields SET properties = " +
             "'{\"placeholder\":\"Enter email\",\"color\":\"red\",\"size\":\"medium\"}' " +
             "WHERE field_name = 'email'");

// Create checkpoint
manager.createCheckpoint("color_red", "user123");
// Binlog position: mysql-bin.000001 @ 1500
// Captured event:
//   Type: UPDATE
//   Before: {id:1, properties: '{"color":"blue",...}'}
//   After:  {id:1, properties: '{"color":"red",...}'}

// User changes size to large
executeUpdate("UPDATE form_fields SET properties = " +
             "'{\"placeholder\":\"Enter email\",\"color\":\"red\",\"size\":\"large\"}' " +
             "WHERE field_name = 'email'");

manager.createCheckpoint("size_large", "user123");
// Captured event:
//   Type: UPDATE
//   Before: {id:1, properties: '{"color":"red","size":"medium",...}'}
//   After:  {id:1, properties: '{"color":"red","size":"large",...}'}

// User wants to undo
manager.undo("user123");
// Executes: UPDATE form_fields SET properties =
//           '{"placeholder":"Enter email","color":"red","size":"medium"}'
//           WHERE id = 1
// Result: Back to "color_red" checkpoint

// User wants to undo again
manager.undo("user123");
// Executes: UPDATE form_fields SET properties =
//           '{"placeholder":"Enter email","color":"blue","size":"medium"}'
//           WHERE id = 1
// Result: Back to "initial" checkpoint

// User wants to redo
manager.redo("user123");
// Executes: UPDATE form_fields SET properties =
//           '{"placeholder":"Enter email","color":"red","size":"medium"}'
//           WHERE id = 1
// Result: Forward to "color_red" checkpoint
```

---

## Advantages of ROW + FULL Approach

✅ **Complete state capture** - Every column value before and after
✅ **Simple undo logic** - Just use before_image
✅ **Simple redo logic** - Just use after_image
✅ **No SQL parsing** - Data is already structured
✅ **Works with any SQL** - Captures complex queries automatically
✅ **Perfect for versioning** - Complete change history
✅ **Multi-table transactions** - Captures all changes atomically
✅ **No schema modifications** - Application tables unchanged
✅ **Scalable** - Only stores changes, not full snapshots

---

## Disadvantages & Mitigation

| Disadvantage | Impact | Mitigation |
|--------------|--------|------------|
| **Larger binlog files** | 2-3x more disk space | Use compression, shorter retention |
| **More event data to process** | Slower event parsing | Filter tables, use indexes |
| **All columns stored** | Unnecessary for small changes | Accept trade-off for simplicity |
| **Binlog purging risk** | Old checkpoints invalid | Capture events to DB table |
| **Requires MySQL config** | Admin access needed | Document requirements clearly |

---

## Recommendation

**Use `binlog_format = ROW` with `binlog_row_image = FULL`** for checkpoint/undo/redo systems because:

1. **Simplicity** - Straightforward undo/redo logic
2. **Completeness** - Always have full state
3. **Reliability** - No missing data issues
4. **Performance** - Event-based is faster than snapshots for large DBs
5. **Storage** - Acceptable overhead (2-3x vs snapshot's 100x)

**Storage efficiency compared to alternatives:**
- Full snapshots: 10 GB per checkpoint ❌
- Binlog with MINIMAL: 10 KB per checkpoint but incomplete data ⚠️
- Binlog with FULL: 25 KB per checkpoint with complete data ✅

The 2.5x storage increase from MINIMAL to FULL is **worth it** for the simplicity and reliability it provides.

---

## Next Steps

Should we proceed with implementation using this approach?

1. ✅ MySQL configuration: ROW format + FULL image
2. ✅ Use mysql-binlog-connector-java for event capture
3. ✅ Store events with before/after images
4. ✅ Implement undo using before_image
5. ✅ Implement redo using after_image
6. ✅ Support multi-user checkpoints
7. ✅ Build CLI tool for testing

Are you ready to start building? 🚀

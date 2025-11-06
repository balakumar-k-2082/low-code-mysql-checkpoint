# MySQL Binary Log Approach for Checkpoints

## Executive Summary

Instead of storing full database snapshots, we leverage MySQL's built-in binary log (binlog) which records every data modification. This approach is similar to Git's commit history - we mark positions in the log and can replay/rollback changes.

---

## How MySQL Binary Logs Work

### What is a Binary Log?

MySQL binary logs are transaction logs that record ALL changes to the database:

```
Time    Event Type    Details
──────────────────────────────────────────────────────────
10:00   INSERT        users: {id:1, name:"Alice"}
10:01   UPDATE        users: SET name="Alice Smith" WHERE id=1
10:02   INSERT        orders: {id:1, user_id:1, total:99.99}
10:03   DELETE        users: WHERE id=2
10:04   UPDATE        form_fields: SET props='{"color":"red"}'
```

Each entry has a **position** (byte offset) in the log file.

### Binary Log Structure

```
┌──────────────────────────────────────┐
│  mysql-bin.000001                    │  ← Binary log file
│  ─────────────────────────────────   │
│  Position 0:    Start                │
│  Position 154:  BEGIN                │
│  Position 217:  INSERT users...      │
│  Position 348:  UPDATE orders...     │
│  Position 502:  COMMIT               │
│  Position 563:  BEGIN                │
│  Position 626:  DELETE users...      │
│  Position 789:  COMMIT               │
└──────────────────────────────────────┘
```

When file gets too large, MySQL rotates to `mysql-bin.000002`, `mysql-bin.000003`, etc.

---

## Binlog-Based Checkpoint Architecture

### Concept: Checkpoints = Binlog Positions

Instead of storing data snapshots, we store **where in the binlog** each checkpoint occurred:

```
Checkpoint 1: mysql-bin.000001 @ position 502
Checkpoint 2: mysql-bin.000001 @ position 789
Checkpoint 3: mysql-bin.000002 @ position 156
```

### Database Schema

```sql
-- Checkpoint metadata with binlog position
CREATE TABLE checkpoint_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Binlog position tracking
    binlog_filename VARCHAR(255) NOT NULL,  -- e.g., "mysql-bin.000001"
    binlog_position BIGINT NOT NULL,        -- e.g., 502
    gtid_set TEXT,                          -- For GTID-based replication

    parent_checkpoint_id BIGINT,            -- For navigation

    INDEX idx_user_created (user_id, created_at),
    INDEX idx_binlog (binlog_filename, binlog_position)
);

-- User's current position in checkpoint history
CREATE TABLE user_checkpoint_position (
    user_id VARCHAR(255) PRIMARY KEY,
    current_checkpoint_id BIGINT NOT NULL,
    FOREIGN KEY (current_checkpoint_id) REFERENCES checkpoint_metadata(id)
);

-- Captured binlog events (for offline replay)
CREATE TABLE checkpoint_binlog_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT NOT NULL,
    event_order INT NOT NULL,
    event_type VARCHAR(50),           -- INSERT, UPDATE, DELETE
    table_name VARCHAR(255),
    event_data JSON,                  -- The actual change
    binlog_position BIGINT,

    INDEX idx_checkpoint (checkpoint_id, event_order),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoint_metadata(id)
);
```

---

## How Operations Work

### 1. Checkpoint Creation

```
User Action: createCheckpoint("before_edit")
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 1. Query Current Binlog Position      │
    │    SHOW MASTER STATUS;                │
    │    → File: mysql-bin.000001           │
    │    → Position: 1234                   │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 2. Store Checkpoint Metadata          │
    │    INSERT INTO checkpoint_metadata    │
    │    VALUES (                           │
    │      user_id: "user123",              │
    │      name: "before_edit",             │
    │      binlog_filename: "mysql-bin...01"│
    │      binlog_position: 1234            │
    │    )                                  │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 3. OPTIONAL: Capture Events           │
    │    Start BinaryLogClient from pos 1234│
    │    Listen for events until next CP    │
    │    Store events in checkpoint_binlog..│
    └───────────────────────────────────────┘
                    │
                    ▼
                ✅ Checkpoint created!
            (Storage: ~100 bytes metadata)
```

**Key Insight:** We only store the POSITION (a few bytes), not the entire database!

---

### 2. Undo Operation (Rollback)

```
User Action: undo()
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 1. Get Current & Previous Checkpoints │
    │    Current: CP3 @ pos 2000            │
    │    Previous: CP2 @ pos 1500           │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 2. Read Binlog Events                 │
    │    From position 1500 to 2000         │
    │    Extract all changes:               │
    │    - INSERT user id=5                 │
    │    - UPDATE form_fields id=2          │
    │    - DELETE orders id=10              │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 3. Generate REVERSE Operations        │
    │    INSERT → DELETE                    │
    │    UPDATE → UPDATE (old values)       │
    │    DELETE → INSERT (old row)          │
    │                                       │
    │    Reverse SQL:                       │
    │    - DELETE FROM users WHERE id=5     │
    │    - UPDATE form_fields SET...        │
    │    - INSERT INTO orders VALUES...     │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 4. Execute Reverse Operations         │
    │    BEGIN;                             │
    │    DELETE FROM users WHERE id=5;      │
    │    UPDATE form_fields...;             │
    │    INSERT INTO orders...;             │
    │    COMMIT;                            │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 5. Update User Position to CP2        │
    └───────────────────────────────────────┘
                    │
                    ▼
            ✅ Database rolled back to CP2!
```

---

### 3. Redo Operation (Forward)

```
User Action: redo()
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 1. Get Current & Next Checkpoints     │
    │    Current: CP2 @ pos 1500            │
    │    Next: CP3 @ pos 2000               │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 2. Read Binlog Events                 │
    │    From position 1500 to 2000         │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 3. Execute FORWARD Operations         │
    │    Replay the exact changes:          │
    │    - INSERT INTO users...             │
    │    - UPDATE form_fields...            │
    │    - DELETE FROM orders...            │
    └───────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────┐
    │ 4. Update User Position to CP3        │
    └───────────────────────────────────────┘
                    │
                    ▼
            ✅ Database replayed to CP3!
```

---

## Java Implementation Using mysql-binlog-connector-java

### Maven Dependency

```xml
<dependency>
    <groupId>com.github.shyiko</groupId>
    <artifactId>mysql-binlog-connector-java</artifactId>
    <version>0.28.1</version>
</dependency>
```

### Example: Reading Binlog Events

```java
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;

public class BinlogCheckpointManager {

    private BinaryLogClient client;

    public void initialize(String host, int port, String username, String password) {
        client = new BinaryLogClient(host, port, username, password);

        // Listen for all events
        client.registerEventListener(event -> {
            EventData data = event.getData();

            if (data instanceof WriteRowsEventData) {
                // INSERT operation
                WriteRowsEventData insertData = (WriteRowsEventData) data;
                String tableName = getTableName(insertData.getTableId());
                List<Serializable[]> rows = insertData.getRows();
                System.out.println("INSERT into " + tableName + ": " + rows);

            } else if (data instanceof UpdateRowsEventData) {
                // UPDATE operation
                UpdateRowsEventData updateData = (UpdateRowsEventData) data;
                String tableName = getTableName(updateData.getTableId());
                List<Map.Entry<Serializable[], Serializable[]>> rows = updateData.getRows();
                // rows contain: before (key) and after (value) states

            } else if (data instanceof DeleteRowsEventData) {
                // DELETE operation
                DeleteRowsEventData deleteData = (DeleteRowsEventData) data;
                String tableName = getTableName(deleteData.getTableId());
                List<Serializable[]> rows = deleteData.getRows();
                System.out.println("DELETE from " + tableName + ": " + rows);
            }
        });
    }

    public CheckpointInfo createCheckpoint(String name, String userId) throws Exception {
        // 1. Get current binlog position
        BinlogPosition position = getCurrentBinlogPosition();

        // 2. Save to database
        String sql = "INSERT INTO checkpoint_metadata " +
                     "(user_id, name, binlog_filename, binlog_position) " +
                     "VALUES (?, ?, ?, ?)";
        // Execute SQL...

        return new CheckpointInfo(name, position.getFilename(), position.getPosition());
    }

    public void undo(String userId) throws Exception {
        // 1. Get current and previous checkpoint
        CheckpointInfo current = getCurrentCheckpoint(userId);
        CheckpointInfo previous = getPreviousCheckpoint(userId);

        // 2. Read binlog events between positions
        List<BinlogEvent> events = readBinlogEvents(
            previous.getPosition(),
            current.getPosition()
        );

        // 3. Generate and execute reverse operations
        List<String> reverseSql = generateReverseSql(events);
        executeInTransaction(reverseSql);

        // 4. Update user position
        updateUserPosition(userId, previous.getId());
    }

    private BinlogPosition getCurrentBinlogPosition() {
        // Execute: SHOW MASTER STATUS
        // Return filename and position
    }

    private List<BinlogEvent> readBinlogEvents(long startPos, long endPos) {
        // Use BinaryLogFileReader to read from file
        // OR use our cached checkpoint_binlog_events table
    }

    private List<String> generateReverseSql(List<BinlogEvent> events) {
        List<String> reverseSql = new ArrayList<>();

        // Iterate events in REVERSE order
        for (int i = events.size() - 1; i >= 0; i--) {
            BinlogEvent event = events.get(i);

            if (event.isInsert()) {
                // INSERT → DELETE
                reverseSql.add(generateDeleteSql(event));
            } else if (event.isUpdate()) {
                // UPDATE → UPDATE with old values
                reverseSql.add(generateUpdateSql(event.getOldValues()));
            } else if (event.isDelete()) {
                // DELETE → INSERT
                reverseSql.add(generateInsertSql(event.getOldValues()));
            }
        }

        return reverseSql;
    }
}
```

---

## Storage Comparison: Binlog vs Snapshot

### Scenario: Database with 1000 tables, 100,000 rows each

| Aspect | Snapshot Approach | Binlog Approach |
|--------|-------------------|-----------------|
| **Storage per checkpoint** | 10 GB (full copy) | 100 bytes (position) + events |
| **10 checkpoints** | 100 GB | ~1 KB metadata + events |
| **Checkpoint creation time** | 30-60 seconds | < 1 second |
| **Undo/Redo time** | 30-60 seconds (restore) | 1-10 seconds (replay) |
| **Scales to 1000 tables?** | ❌ NO | ✅ YES |
| **Memory usage** | High (load all data) | Low (stream events) |
| **Complexity** | Simple | Complex |

**Winner for large databases: BINLOG APPROACH** 🏆

---

## Challenges & Solutions

### Challenge 1: Binlog Rotation

**Problem:** MySQL rotates binlog files (`mysql-bin.000001` → `mysql-bin.000002`)

**Solution:**
- Store filename + position in checkpoint
- When reading, handle multiple files
- Track binlog rotation events

```java
// Handle crossing file boundaries
if (currentFile.equals("mysql-bin.000001") && targetFile.equals("mysql-bin.000002")) {
    readFromEnd("mysql-bin.000001");
    readFromStart("mysql-bin.000002", targetPosition);
}
```

### Challenge 2: Binlog Purging

**Problem:** MySQL automatically deletes old binlog files

**Solution:**
- Configure MySQL: `SET GLOBAL expire_logs_days = 30;`
- OR capture events and store in `checkpoint_binlog_events` table
- Detect when binlog is missing and fail gracefully

```java
try {
    readBinlogFile("mysql-bin.000001");
} catch (FileNotFoundException e) {
    if (hasStoredEvents(checkpointId)) {
        // Use stored events from checkpoint_binlog_events table
        return readStoredEvents(checkpointId);
    } else {
        throw new CheckpointExpiredException("Binlog purged, cannot restore");
    }
}
```

### Challenge 3: Multi-User Isolation

**Problem:** Binlog contains ALL users' changes mixed together

**Solution:**
- Filter events by table/user when replaying
- Store user context in checkpoint metadata
- Each user gets their own checkpoint chain

```java
// When creating checkpoint, optionally capture relevant events
public void createCheckpoint(String name, String userId) {
    BinlogPosition pos = getCurrentPosition();

    // Start capturing events for this user's session
    startEventCapture(userId, pos, (event) -> {
        // Only capture events from this user's connection/session
        if (event.getSessionId().equals(userSession)) {
            storeEvent(checkpointId, event);
        }
    });
}
```

### Challenge 4: Schema Changes (DDL)

**Problem:** Binlog contains DDL (ALTER TABLE, DROP TABLE)

**Solution:**
- Track DDL events separately
- When rolling back, reverse DDL changes first
- Warn user if schema changes detected

```java
if (event.getEventType() == EventType.QUERY) {
    QueryEventData query = (QueryEventData) event.getData();
    if (query.getSql().startsWith("ALTER") || query.getSql().startsWith("DROP")) {
        throw new UnsupportedOperationException(
            "Cannot undo schema changes. Checkpoint includes DDL: " + query.getSql()
        );
    }
}
```

### Challenge 5: Large Transactions

**Problem:** Single transaction with 100,000 INSERTs

**Solution:**
- Stream events instead of loading all in memory
- Process in batches
- Show progress indicator

```java
List<BinlogEvent> events = readBinlogEventsStreaming(startPos, endPos);
int batchSize = 1000;
int processed = 0;

for (List<BinlogEvent> batch : partition(events, batchSize)) {
    executeReverseBatch(batch);
    processed += batch.size();
    System.out.println("Progress: " + processed + "/" + events.size());
}
```

---

## Prerequisites & Configuration

### MySQL Configuration

Enable binary logging in `my.cnf`:

```ini
[mysqld]
# Enable binary logging
log-bin = mysql-bin
binlog_format = ROW          # IMPORTANT: Must be ROW format
binlog_row_image = FULL      # Store complete before/after images
expire_logs_days = 7         # Keep logs for 7 days
max_binlog_size = 100M       # Rotate at 100MB
```

### Database User Permissions

```sql
-- User needs these privileges
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mydb.* TO 'checkpoint_user'@'%';

FLUSH PRIVILEGES;
```

### Verify Binary Logging is Enabled

```sql
-- Check if binlog is enabled
SHOW VARIABLES LIKE 'log_bin';
-- Should return: log_bin = ON

-- Check binlog format
SHOW VARIABLES LIKE 'binlog_format';
-- Should return: binlog_format = ROW

-- View current binlog files
SHOW BINARY LOGS;
-- Returns list of mysql-bin.000001, mysql-bin.000002, etc.

-- View current position
SHOW MASTER STATUS;
-- Returns: File, Position, Binlog_Do_DB, Binlog_Ignore_DB
```

---

## Advantages of Binlog Approach

✅ **Minimal storage** - Only store positions + captured events
✅ **Fast checkpoint creation** - Just record position (< 1 sec)
✅ **Scales to any DB size** - Works with 1000 tables, millions of rows
✅ **Low memory footprint** - Stream events, don't load all data
✅ **Leverages MySQL native** - No reinventing the wheel
✅ **Point-in-time accuracy** - Exact transaction boundaries
✅ **Production-ready** - Used by MySQL replication, proven at scale

---

## Disadvantages of Binlog Approach

❌ **Complex implementation** - Requires understanding binlog format
❌ **MySQL configuration required** - Must enable binlog (ROW format)
❌ **Binlog purging risk** - Old checkpoints become invalid if binlog deleted
❌ **Event capture needed** - May need to store events to avoid purging issues
❌ **Schema changes tricky** - DDL operations are hard to reverse
❌ **Requires REPLICATION privileges** - Security consideration
❌ **Reverse operation generation** - Must carefully construct inverse SQL

---

## Hybrid Approach (Recommended)

Combine binlog tracking with selective event capture:

```
Checkpoint Creation:
1. Record binlog position (always)
2. Optionally capture events between checkpoints
3. Store captured events in checkpoint_binlog_events table

Restoration:
1. Try to read from binlog files (if still available)
2. Fall back to stored events (if binlog purged)
3. If neither available, fail with clear error message
```

This gives us:
- ✅ Fast checkpoint creation (just position)
- ✅ Works even after binlog rotation/purging (stored events)
- ✅ Efficient storage (only store events between checkpoints, not full snapshots)

---

## Implementation Strategy

### Phase 1: Basic Binlog Tracking
- Record binlog position at checkpoint
- Read events using mysql-binlog-connector-java
- Basic undo (reverse last checkpoint)

### Phase 2: Event Capture
- Store events in checkpoint_binlog_events table
- Handle binlog purging gracefully
- Support redo operation

### Phase 3: Advanced Features
- Multi-user isolation
- Handle schema changes
- Optimize reverse SQL generation
- Add conflict resolution

---

## Next Steps

Should we proceed with implementing the binlog approach?

**Questions:**
1. Do you have control over MySQL configuration (can enable binlog)?
2. Are you comfortable with the added complexity vs snapshot approach?
3. Should we build Phase 1 first (basic binlog tracking) before event capture?
4. Any concerns about requiring REPLICATION privileges?

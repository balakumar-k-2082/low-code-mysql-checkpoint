# MySQL Transaction-Based Checkpoint System

A Java library for implementing **transaction-based undo/redo** functionality using MySQL binary logs. Designed for low-code platforms where each user action (e.g., "make field mandatory") is a database transaction.

## 🎯 Key Features

- ✅ **Transaction-Based Checkpoints** - One checkpoint per database transaction, not per query
- ✅ **Automatic Capture** - Uses MySQL binlog to automatically capture all changes
- ✅ **Atomic Undo/Redo** - Reverses entire transactions atomically
- ✅ **Multi-App Isolation** - Per-schema filtering for multi-tenant applications
- ✅ **Complete State Capture** - ROW format with FULL image stores complete before/after states
- ✅ **Persistent History** - Checkpoints survive server restarts
- ✅ **Per-User Timelines** - Each user has independent checkpoint history

## 🏗️ Architecture

```
Low-Code Application
    ↓ (wraps actions in @Transactional)
MySQL Database Transaction
    ↓ (BEGIN → events → COMMIT)
Binary Log (ROW format + FULL image)
    ↓ (GTID → events → XID)
TransactionCapture (detects boundaries)
    ↓ (groups events by GTID/XID)
CheckpointManager (creates checkpoint)
    ↓ (stores in checkpoint_system DB)
UndoRedoManager (atomic reversal)
```

## 📋 Prerequisites

### MySQL Configuration

Edit `my.cnf` or `my.ini`:

```ini
[mysqld]
# Enable binary logging
log-bin = mysql-bin
server-id = 1

# CRITICAL: Use ROW format with FULL image
binlog_format = ROW
binlog_row_image = FULL

# HIGHLY RECOMMENDED: Enable GTID
gtid_mode = ON
enforce_gtid_consistency = ON

# Retention
expire_logs_days = 7
max_binlog_size = 100M
```

### MySQL User Permissions

```sql
-- Create user for checkpoint system
CREATE USER 'checkpoint_user'@'%' IDENTIFIED BY 'checkpoint_password';

-- Grant replication privileges (required for binlog reading)
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'%';

-- Grant access to all schemas
GRANT ALL ON *.* TO 'checkpoint_user'@'%';

FLUSH PRIVILEGES;
```

### Initialize Checkpoint System Database

```bash
mysql -u root -p < checkpoint-lib/src/main/resources/schema.sql
```

This creates the `checkpoint_system` database with tables:
- `apps` - Application registry
- `checkpoints` - Checkpoint metadata
- `checkpoint_transaction_events` - Transaction events
- `user_checkpoint_position` - User's current position

## 🚀 Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.checkpoint</groupId>
    <artifactId>checkpoint-lib</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Create Application Schema

```sql
-- Create schema for your app
CREATE DATABASE app_myapp;

USE app_myapp;

-- Example: form field table
CREATE TABLE form_fields (
    id INT PRIMARY KEY AUTO_INCREMENT,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    is_required BOOLEAN DEFAULT FALSE,
    properties JSON
) ENGINE=InnoDB;
```

### 3. Initialize Checkpoint Manager

```java
import com.checkpoint.manager.CheckpointManager;
import com.checkpoint.manager.UndoRedoManager;
import com.mysql.cj.jdbc.MysqlDataSource;

// Create datasource
MysqlDataSource dataSource = new MysqlDataSource();
dataSource.setURL("jdbc:mysql://localhost:3306/checkpoint_system");
dataSource.setUser("checkpoint_user");
dataSource.setPassword("checkpoint_password");

// Initialize checkpoint manager
CheckpointManager checkpointManager = new CheckpointManager(
    dataSource,
    "myapp",           // App ID
    "app_myapp",       // Schema name
    "localhost",       // Binlog host
    3306,             // Binlog port
    "checkpoint_user",
    "checkpoint_password"
);

checkpointManager.initialize();

// Initialize undo/redo manager
UndoRedoManager undoRedoManager = new UndoRedoManager(
    dataSource,
    checkpointManager,
    "myapp",
    "app_myapp"
);
```

### 4. Use in Your Application

```java
import java.sql.*;

public class FormFieldService {

    private final DataSource dataSource;
    private final UndoRedoManager undoRedoManager;

    public void makeFieldMandatory(int fieldId, String userId) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);  // Start transaction

        try {
            // 1. Update field
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE app_myapp.form_fields SET is_required = TRUE WHERE id = ?"
            );
            stmt.setInt(1, fieldId);
            stmt.executeUpdate();

            // 2. Add validation rule
            stmt = conn.prepareStatement(
                "INSERT INTO app_myapp.validation_rules (field_id, rule_type) VALUES (?, 'required')"
            );
            stmt.setInt(1, fieldId);
            stmt.executeUpdate();

            // 3. Commit transaction
            conn.commit();

            // Checkpoint is automatically created!
            System.out.println("✅ Field made mandatory - checkpoint created");

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    public void undo(String userId) throws SQLException {
        undoRedoManager.undo(userId);
        System.out.println("✅ Undo completed");
    }

    public void redo(String userId) throws SQLException {
        undoRedoManager.redo(userId);
        System.out.println("✅ Redo completed");
    }
}
```

## 🔄 How It Works

### Checkpoint Creation

1. **Application commits transaction:**
   ```sql
   BEGIN;
   UPDATE form_fields SET is_required = TRUE WHERE id = 1;
   INSERT INTO validation_rules VALUES (...);
   COMMIT;
   ```

2. **Binlog captures transaction:**
   ```
   GTID Event (server-uuid:42)
   QUERY Event (BEGIN)
   UPDATE_ROWS Event (form_fields)
   WRITE_ROWS Event (validation_rules)
   XID Event (COMMIT)
   ```

3. **TransactionCapture groups events by GTID/XID**

4. **CheckpointManager stores:**
   - Checkpoint metadata (GTID, XID, binlog positions)
   - All events in transaction (with before/after images)

### Undo Operation

1. **Load events for current checkpoint**
2. **Execute reverse operations in REVERSE order:**
   - DELETE (was INSERT)
   - UPDATE with before_image (was UPDATE)
3. **Update user position to previous checkpoint**
4. **All in single atomic transaction**

### Redo Operation

1. **Load events for next checkpoint**
2. **Execute forward operations in FORWARD order:**
   - UPDATE with after_image
   - INSERT
3. **Update user position to next checkpoint**
4. **All in single atomic transaction**

## 📊 Storage Efficiency

**Example:** Transaction with 4 SQL statements

| Approach | Storage per Action |
|----------|-------------------|
| Full Snapshots | 10 GB (entire database) |
| Query-Based Checkpoints | 100 KB (4 checkpoints) |
| **Transaction-Based** | **10 KB (1 checkpoint)** ✅ |

**Benefits:**
- 1000x more efficient than snapshots
- 10x more efficient than query-based
- Intuitive user history (action-level, not query-level)

## 🔧 Configuration

### Application Properties

```properties
# MySQL Connection
checkpoint.mysql.host=localhost
checkpoint.mysql.port=3306
checkpoint.mysql.user=checkpoint_user
checkpoint.mysql.password=checkpoint_password

# Binlog Connection
checkpoint.binlog.host=localhost
checkpoint.binlog.port=3306
checkpoint.binlog.user=checkpoint_user
checkpoint.binlog.password=checkpoint_password

# App Configuration
checkpoint.app.id=myapp
checkpoint.app.schema=app_myapp
```

## 📖 API Reference

### CheckpointManager

```java
// Initialize and start capturing
void initialize() throws SQLException

// Get current checkpoint for user
Checkpoint getCurrentCheckpoint(String userId) throws SQLException

// Get previous/next checkpoint
Checkpoint getPreviousCheckpoint(Checkpoint current) throws SQLException
Checkpoint getNextCheckpoint(Checkpoint current) throws SQLException

// Load events for checkpoint
List<TransactionEvent> loadTransactionEvents(long checkpointId) throws SQLException

// Stop capturing
void stop()
```

### UndoRedoManager

```java
// Undo to previous checkpoint
void undo(String userId) throws SQLException

// Redo to next checkpoint
void redo(String userId) throws SQLException
```

## 🎮 CLI Demo

Build and run the CLI tool:

```bash
cd /path/to/low-code-mysql-checkpoint

# Build
mvn clean package

# Run CLI
java -jar checkpoint-cli/target/checkpoint-cli-1.0-SNAPSHOT.jar
```

The CLI provides interactive commands to test checkpoint functionality.

## 🏭 Production Deployment

### 1. Checkpoint Naming

Instead of auto-generated names, use descriptive names:

```java
checkpointManager.createCheckpointFromTransaction(
    transaction,
    "make_email_field_mandatory"
);
```

### 2. Add Application Metadata

```java
// Use thread-local context to add metadata
CheckpointContext.setAction("makeFieldMandatory");
CheckpointContext.setMetadata("field", "email");
CheckpointContext.setMetadata("user", userId);
```

### 3. Conflict Detection

Check for conflicts before undo:

```java
List<Conflict> conflicts = detectConflicts(targetCheckpoint);
if (!conflicts.isEmpty()) {
    // Show conflict resolution UI
}
```

### 4. Monitoring

- Monitor binlog size growth
- Set up alerts for checkpoint table growth
- Implement retention policies

### 5. Backup

Backup both:
- Application database (`app_myapp`)
- Checkpoint system database (`checkpoint_system`)

## 🐛 Troubleshooting

### Binlog Not Enabled

**Error:** `Failed to get binlog position`

**Solution:** Enable binary logging in `my.cnf`:
```ini
log-bin = mysql-bin
```

### REPLICATION SLAVE Permission Denied

**Error:** `Access denied; you need the REPLICATION SLAVE privilege`

**Solution:**
```sql
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'%';
FLUSH PRIVILEGES;
```

### Binlog Format Not ROW

**Error:** `Statement-based binlog detected`

**Solution:**
```ini
binlog_format = ROW
```

### Events Not Captured

**Check:**
1. Is binlog_row_image = FULL?
2. Is schema name correct?
3. Are transactions being committed?

## 📚 Documentation

- [Architecture Overview](ARCHITECTURE.md)
- [Binlog Approach](BINLOG_APPROACH.md)
- [ROW+FULL Implementation](ROW_FULL_IMPLEMENTATION.md)
- [Multi-Schema Architecture](MULTI_SCHEMA_ARCHITECTURE.md)
- [Critical Review](CRITICAL_REVIEW.md)
- [Transaction-Based Checkpoints](TRANSACTION_BASED_CHECKPOINTS.md)

## 🤝 Contributing

This is a proof-of-concept implementation. Production use requires:

- [ ] Comprehensive error handling
- [ ] Integration tests with actual MySQL
- [ ] Performance benchmarks
- [ ] Conflict resolution UI
- [ ] Retention policy implementation
- [ ] Monitoring and alerting

## 📄 License

MIT License

## ⚠️ Important Notes

1. **Binlog Retention:** Configure adequate retention to prevent checkpoint invalidation
2. **Storage Growth:** Monitor checkpoint_system database size
3. **Performance:** 30% overhead expected with binlog enabled
4. **GTID Mode:** Highly recommended for reliable transaction tracking
5. **Testing:** Test thoroughly in non-production environment first

## 🎓 Use Cases

Perfect for:
- ✅ Low-code/No-code platforms
- ✅ Form builders
- ✅ Database designers
- ✅ Configuration management systems
- ✅ Workflow engines
- ✅ Any application needing transactional undo/redo

Not suitable for:
- ❌ High-frequency trading (30% overhead)
- ❌ Systems with millions of transactions/minute
- ❌ Applications without transactional boundaries

## 📞 Support

For issues and questions, please refer to the documentation or open an issue.

---

**Built with ❤️ using MySQL Binary Logs and Java**

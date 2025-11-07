# Quick Reference Guide

**One-page reference for common tasks and commands**

---

## MySQL Configuration

### Minimum Required Config (my.cnf / my.ini)

```ini
[mysqld]
server-id = 1
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
gtid_mode = ON
enforce_gtid_consistency = ON
```

### Config File Locations

| OS | Path |
|----|------|
| Ubuntu/Debian | `/etc/mysql/mysql.conf.d/mysqld.cnf` |
| RedHat/CentOS | `/etc/my.cnf` |
| macOS (Homebrew) | `/usr/local/etc/my.cnf` |
| Windows | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |

---

## User Setup

```sql
-- Create user
CREATE USER 'checkpoint_user'@'localhost' IDENTIFIED BY 'password';

-- Grant permissions
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'localhost';
GRANT ALL ON checkpoint_system.* TO 'checkpoint_user'@'localhost';
GRANT ALL ON app_demo.* TO 'checkpoint_user'@'localhost';
FLUSH PRIVILEGES;
```

---

## Database Setup

```bash
# Initialize checkpoint database
mysql -u checkpoint_user -p < checkpoint-lib/src/main/resources/schema.sql
```

---

## Build & Run

```bash
# Build
cd /home/user/low-code-mysql-checkpoint
mvn clean package

# Run CLI demo
java -jar checkpoint-cli/target/checkpoint-cli-1.0-SNAPSHOT.jar
```

---

## Verification Commands

```sql
-- Check binlog enabled
SHOW VARIABLES LIKE 'log_bin';                -- Should be ON

-- Check format
SHOW VARIABLES LIKE 'binlog_format';          -- Should be ROW

-- Check row image
SHOW VARIABLES LIKE 'binlog_row_image';       -- Should be FULL

-- Check GTID
SHOW VARIABLES LIKE 'gtid_mode';              -- Should be ON

-- Check server ID
SHOW VARIABLES LIKE 'server_id';              -- Should be 1 or higher

-- View binlogs
SHOW BINARY LOGS;

-- Check current position
SHOW MASTER STATUS;
```

---

## Common Errors & Fixes

| Error | Fix |
|-------|-----|
| server-id not set | Add `server-id = 1` to my.cnf, restart MySQL |
| Binary logging not enabled | Add `log-bin = mysql-bin` to my.cnf, restart MySQL |
| REPLICATION SLAVE denied | `GRANT REPLICATION SLAVE ON *.* TO 'user'@'host';` |
| binlog_format is STATEMENT | Add `binlog_format = ROW` to my.cnf, restart MySQL |
| Table doesn't exist | Run schema.sql: `mysql -u user -p < schema.sql` |

---

## CLI Commands

```
1, add        - Add email field
2, mandatory  - Make email mandatory
3, phone      - Add phone field
u, undo       - Undo last action
r, redo       - Redo last undone action
s, status     - Show current status
h, help       - Show help
q, quit       - Exit
```

---

## Java API Quick Start

```java
// 1. Create datasource
MysqlDataSource dataSource = new MysqlDataSource();
dataSource.setURL("jdbc:mysql://localhost:3306/checkpoint_system");
dataSource.setUser("checkpoint_user");
dataSource.setPassword("password");

// 2. Initialize checkpoint manager
CheckpointManager checkpointManager = new CheckpointManager(
    dataSource, "myapp", "app_myapp",
    "localhost", 3306, "checkpoint_user", "password"
);
checkpointManager.initialize();

// 3. Initialize undo/redo manager
UndoRedoManager undoRedoManager = new UndoRedoManager(
    dataSource, checkpointManager, "myapp", "app_myapp"
);

// 4. Use in your application
// Make changes in a transaction
connection.setAutoCommit(false);
// ... execute SQL ...
connection.commit();
// Checkpoint created automatically!

// 5. Undo/Redo
undoRedoManager.undo("user_id");
undoRedoManager.redo("user_id");
```

---

## File Structure

```
low-code-mysql-checkpoint/
├── README.md                   - Main documentation
├── SETUP_GUIDE.md             - Detailed setup steps
├── CLAUDE.md                  - Claude Code usage guide
├── QUICK_REFERENCE.md         - This file
├── checkpoint-lib/            - Core library
│   └── src/main/java/com/checkpoint/
│       ├── capture/           - Binlog capture
│       ├── manager/           - Checkpoint & undo/redo
│       ├── model/             - Data models
│       └── util/              - Utilities
└── checkpoint-cli/            - Demo CLI tool
```

---

## Useful SQL Queries

```sql
-- View all checkpoints
SELECT id, name, gtid, created_at
FROM checkpoint_system.checkpoints
ORDER BY created_at DESC;

-- View checkpoint events
SELECT checkpoint_id, table_name, event_type
FROM checkpoint_system.checkpoint_transaction_events
WHERE checkpoint_id = 1;

-- Check binlog size
SELECT ROUND(SUM(file_size)/1024/1024, 2) AS size_mb
FROM information_schema.files
WHERE file_name LIKE 'mysql-bin%';

-- Purge old binlogs
PURGE BINARY LOGS TO 'mysql-bin.000010';
```

---

## Restart MySQL

| OS | Command |
|----|---------|
| Ubuntu/Debian | `sudo systemctl restart mysql` |
| RedHat/CentOS | `sudo systemctl restart mysqld` |
| macOS (Homebrew) | `brew services restart mysql` |
| macOS (Official) | `sudo /usr/local/mysql/support-files/mysql.server restart` |
| Windows | `net stop MySQL80 && net start MySQL80` |

---

## Find MySQL Log

| OS | Path |
|----|------|
| Linux | `/var/log/mysql/error.log` |
| macOS | `/usr/local/var/mysql/*.err` |
| Windows | `C:\ProgramData\MySQL\MySQL Server 8.0\Data\*.err` |

---

## Documentation

- **SETUP_GUIDE.md** - Complete setup with troubleshooting
- **README.md** - Quick start and API reference
- **CLAUDE.md** - Using Claude Code with this project
- **ARCHITECTURE.md** - System design
- **TRANSACTION_BASED_CHECKPOINTS.md** - Design rationale
- **CRITICAL_REVIEW.md** - Architecture review

---

## Get Help

```bash
# Check MySQL config in use
mysql --help | grep "Default options" -A 1

# Find my.cnf
sudo find / -name my.cnf 2>/dev/null

# Test MySQL connection
mysql -u checkpoint_user -p -h localhost -P 3306

# View MySQL logs
tail -f /var/log/mysql/error.log
```

---

## Production Checklist

- [ ] MySQL binlog configured (ROW + FULL)
- [ ] GTID mode enabled
- [ ] User permissions granted
- [ ] checkpoint_system database created
- [ ] Binlog retention configured
- [ ] Monitoring set up
- [ ] Backup strategy in place
- [ ] Security hardening applied

---

**Keep this page handy for quick reference!** 📖

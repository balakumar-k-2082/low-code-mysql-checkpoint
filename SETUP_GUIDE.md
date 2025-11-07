# MySQL Checkpoint System - Complete Setup Guide

This guide provides **step-by-step instructions** for setting up the MySQL Transaction-Based Checkpoint System from scratch.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Step 1: Locate and Edit MySQL Configuration](#step-1-locate-and-edit-mysql-configuration)
3. [Step 2: Restart MySQL](#step-2-restart-mysql)
4. [Step 3: Verify Configuration](#step-3-verify-configuration)
5. [Step 4: Create Database User](#step-4-create-database-user)
6. [Step 5: Initialize Checkpoint Database](#step-5-initialize-checkpoint-database)
7. [Step 6: Build the Project](#step-6-build-the-project)
8. [Step 7: Run the Demo](#step-7-run-the-demo)
9. [Troubleshooting](#troubleshooting)
10. [Production Deployment](#production-deployment)

---

## Prerequisites

- MySQL 5.7+ or MySQL 8.0+ (MySQL 8.0+ recommended for GTID support)
- Java 11 or higher
- Maven 3.6+
- Root access to MySQL server (or ability to modify configuration)

---

## Step 1: Locate and Edit MySQL Configuration

### Finding my.cnf (or my.ini)

The location varies by operating system:

#### **Linux/Unix:**

```bash
# Common locations (check in this order):
/etc/my.cnf
/etc/mysql/my.cnf
/etc/mysql/mysql.conf.d/mysqld.cnf
/usr/local/mysql/etc/my.cnf
~/.my.cnf

# Find all my.cnf files:
sudo find / -name my.cnf 2>/dev/null

# Check which config file MySQL is actually using:
mysql --help | grep "Default options" -A 1
```

#### **macOS:**

```bash
# Homebrew installation:
/usr/local/etc/my.cnf
/opt/homebrew/etc/my.cnf

# Official MySQL installation:
/etc/my.cnf
/etc/mysql/my.cnf

# Find it:
sudo find /usr/local /opt /etc -name my.cnf 2>/dev/null
```

#### **Windows:**

```
C:\ProgramData\MySQL\MySQL Server 8.0\my.ini
C:\Program Files\MySQL\MySQL Server 8.0\my.ini
C:\Windows\my.ini
```

### Edit Configuration

Once you've located the file, edit it with root/admin privileges:

```bash
# Linux/macOS:
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf

# Or using vim:
sudo vim /etc/mysql/mysql.conf.d/mysqld.cnf
```

### Add Configuration

Find the `[mysqld]` section and add these lines:

```ini
[mysqld]
# Server identification (REQUIRED!)
server-id = 1

# Enable binary logging (REQUIRED!)
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL

# GTID mode (HIGHLY RECOMMENDED)
gtid_mode = ON
enforce_gtid_consistency = ON

# Binary log retention (adjust as needed)
expire_logs_days = 7
max_binlog_size = 100M

# Optional: Better transaction tracking
binlog_transaction_dependency_tracking = WRITESET
```

**IMPORTANT:**
- `server-id` is **MANDATORY**. Each MySQL instance must have a unique server-id.
- Use `server-id = 1` for your main server
- If you have multiple MySQL servers, use different IDs (1, 2, 3, etc.)

### Example Complete mysqld Section

```ini
[mysqld]
#
# Basic Settings
#
user = mysql
pid-file = /var/run/mysqld/mysqld.pid
socket = /var/run/mysqld/mysqld.sock
port = 3306
datadir = /var/lib/mysql

#
# Binary Logging Configuration (CHECKPOINT SYSTEM)
#
server-id = 1
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
gtid_mode = ON
enforce_gtid_consistency = ON
expire_logs_days = 7
max_binlog_size = 100M

#
# Other settings...
#
```

---

## Step 2: Restart MySQL

After editing the configuration, restart MySQL:

### **Linux (Ubuntu/Debian):**

```bash
sudo systemctl restart mysql

# Or:
sudo service mysql restart
```

### **Linux (RedHat/CentOS):**

```bash
sudo systemctl restart mysqld

# Or:
sudo service mysqld restart
```

### **macOS (Homebrew):**

```bash
brew services restart mysql

# Or manually:
mysql.server restart
```

### **macOS (Official MySQL):**

```bash
# From System Preferences > MySQL
# Click "Stop MySQL Server", then "Start MySQL Server"

# Or from terminal:
sudo /usr/local/mysql/support-files/mysql.server restart
```

### **Windows:**

```cmd
# Run as Administrator:
net stop MySQL80
net start MySQL80

# Or use Services:
# Win+R -> services.msc -> Find MySQL80 -> Restart
```

---

## Step 3: Verify Configuration

Connect to MySQL and verify the configuration:

```bash
mysql -u root -p
```

Then run these verification queries:

```sql
-- 1. Check if binary logging is enabled
SHOW VARIABLES LIKE 'log_bin';
-- Expected: log_bin = ON

-- 2. Check binlog format
SHOW VARIABLES LIKE 'binlog_format';
-- Expected: binlog_format = ROW

-- 3. Check row image
SHOW VARIABLES LIKE 'binlog_row_image';
-- Expected: binlog_row_image = FULL

-- 4. Check GTID mode
SHOW VARIABLES LIKE 'gtid_mode';
-- Expected: gtid_mode = ON

-- 5. Check server ID
SHOW VARIABLES LIKE 'server_id';
-- Expected: server_id = 1 (or your chosen ID)

-- 6. View current binary logs
SHOW BINARY LOGS;
-- Should show mysql-bin.000001, mysql-bin.000002, etc.

-- 7. Check master status
SHOW MASTER STATUS;
-- Should show current binlog file and position
```

### Expected Output

```
mysql> SHOW VARIABLES LIKE 'log_bin';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| log_bin       | ON    |
+---------------+-------+

mysql> SHOW VARIABLES LIKE 'binlog_format';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| binlog_format | ROW   |
+---------------+-------+

mysql> SHOW VARIABLES LIKE 'binlog_row_image';
+------------------+-------+
| Variable_name    | Value |
+------------------+-------+
| binlog_row_image | FULL  |
+------------------+-------+

mysql> SHOW MASTER STATUS;
+------------------+----------+--------------+------------------+-------------------+
| File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+------------------+----------+--------------+------------------+-------------------+
| mysql-bin.000001 |      156 |              |                  | server-1:1-5      |
+------------------+----------+--------------+------------------+-------------------+
```

---

## Step 4: Create Database User

Connect to MySQL as root and create a dedicated user:

```bash
mysql -u root -p
```

Then execute these commands:

```sql
-- 1. Create user
CREATE USER 'checkpoint_user'@'localhost' IDENTIFIED BY 'checkpoint_password';

-- For remote access (if needed):
CREATE USER 'checkpoint_user'@'%' IDENTIFIED BY 'checkpoint_password';

-- 2. Grant replication privileges (REQUIRED for binlog reading)
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'localhost';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'localhost';

-- For remote access:
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'%';

-- 3. Grant database privileges
-- Option A: Grant all privileges (for development)
GRANT ALL PRIVILEGES ON *.* TO 'checkpoint_user'@'localhost';

-- Option B: Grant specific privileges (for production)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
ON checkpoint_system.* TO 'checkpoint_user'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
ON app_demo.* TO 'checkpoint_user'@'localhost';

-- 4. Apply changes
FLUSH PRIVILEGES;

-- 5. Verify permissions
SHOW GRANTS FOR 'checkpoint_user'@'localhost';
```

### Expected Grants Output

```sql
mysql> SHOW GRANTS FOR 'checkpoint_user'@'localhost';
+-----------------------------------------------------------------------------------+
| Grants for checkpoint_user@localhost                                              |
+-----------------------------------------------------------------------------------+
| GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO `checkpoint_user`@`localhost` |
| GRANT ALL PRIVILEGES ON *.* TO `checkpoint_user`@`localhost`                      |
+-----------------------------------------------------------------------------------+
```

---

## Step 5: Initialize Checkpoint Database

### Method 1: Using SQL File

```bash
# Navigate to project directory
cd /home/user/low-code-mysql-checkpoint

# Run schema creation script
mysql -u checkpoint_user -p < checkpoint-lib/src/main/resources/schema.sql
```

### Method 2: Manual Creation

```bash
mysql -u checkpoint_user -p
```

Then paste and execute:

```sql
-- Create checkpoint system database
CREATE DATABASE IF NOT EXISTS checkpoint_system;
USE checkpoint_system;

-- App registry
CREATE TABLE IF NOT EXISTS apps (
    app_id VARCHAR(255) PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('active', 'inactive') DEFAULT 'active',
    INDEX idx_schema (schema_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Checkpoint metadata
CREATE TABLE IF NOT EXISTS checkpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    description TEXT,
    action_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    gtid VARCHAR(255),
    xid BIGINT,
    transaction_length BIGINT,
    binlog_filename VARCHAR(255) NOT NULL,
    binlog_start_position BIGINT NOT NULL,
    binlog_end_position BIGINT NOT NULL,
    parent_checkpoint_id BIGINT,
    INDEX idx_app_user (app_id, user_id, created_at),
    INDEX idx_gtid (gtid),
    INDEX idx_binlog (binlog_filename, binlog_start_position),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (parent_checkpoint_id) REFERENCES checkpoints(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transaction events
CREATE TABLE IF NOT EXISTS checkpoint_transaction_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    checkpoint_id BIGINT NOT NULL,
    schema_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    event_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    event_sequence INT NOT NULL,
    before_image TEXT,
    after_image TEXT,
    INDEX idx_checkpoint (checkpoint_id, event_sequence),
    INDEX idx_schema_table (schema_name, table_name),
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- User checkpoint position
CREATE TABLE IF NOT EXISTS user_checkpoint_position (
    app_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    current_checkpoint_id BIGINT NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, user_id),
    FOREIGN KEY (app_id) REFERENCES apps(app_id),
    FOREIGN KEY (current_checkpoint_id) REFERENCES checkpoints(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Verify Tables Created

```sql
USE checkpoint_system;
SHOW TABLES;

-- Expected output:
-- +-------------------------------+
-- | Tables_in_checkpoint_system   |
-- +-------------------------------+
-- | apps                          |
-- | checkpoint_transaction_events |
-- | checkpoints                   |
-- | user_checkpoint_position      |
-- +-------------------------------+
```

---

## Step 6: Build the Project

```bash
# Navigate to project directory
cd /home/user/low-code-mysql-checkpoint

# Clean and build
mvn clean package

# Expected output:
# [INFO] ------------------------------------------------------------------------
# [INFO] Reactor Summary:
# [INFO]
# [INFO] MySQL Checkpoint System ............................ SUCCESS
# [INFO] Checkpoint Library ................................. SUCCESS
# [INFO] Checkpoint CLI ..................................... SUCCESS
# [INFO] ------------------------------------------------------------------------
# [INFO] BUILD SUCCESS
# [INFO] ------------------------------------------------------------------------
```

### Verify Build

```bash
# Check that JARs were created:
ls -lh checkpoint-lib/target/*.jar
ls -lh checkpoint-cli/target/*.jar

# Expected:
# checkpoint-lib/target/checkpoint-lib-1.0-SNAPSHOT.jar
# checkpoint-cli/target/checkpoint-cli-1.0-SNAPSHOT.jar
```

---

## Step 7: Run the Demo

### Start the CLI

```bash
java -jar checkpoint-cli/target/checkpoint-cli-1.0-SNAPSHOT.jar
```

### Interactive Setup

The CLI will prompt you for connection details:

```
═══════════════════════════════════════════════════════════
   MySQL Transaction-Based Checkpoint System - CLI Demo
═══════════════════════════════════════════════════════════

MySQL Host [localhost]: localhost
MySQL Port [3306]: 3306
MySQL User [root]: checkpoint_user
MySQL Password: checkpoint_password

 Connecting to MySQL...
📦 Setting up demo schema...
✅ Demo schema ready: app_demo
🔧 Initializing checkpoint system...
✅ Checkpoint system initialized!
```

### Test Commands

Try these commands in order:

```
> help                    # Show all commands
> 1                       # Add email field
> status                  # Show current state
> 2                       # Make email mandatory
> status                  # See changes
> 3                       # Add phone field
> status                  # See all fields
> undo                    # Undo phone field
> status                  # Phone is gone
> undo                    # Undo make mandatory
> status                  # Email is optional again
> redo                    # Redo make mandatory
> status                  # Email is required again
> quit                    # Exit
```

### Expected Output

```
╔═══════════════════════════════════════════════════════════╗
║                       COMMANDS                            ║
╠═══════════════════════════════════════════════════════════╣
║  1, add        - Add 'email' field                        ║
║  2, mandatory  - Make 'email' field mandatory             ║
║  3, phone      - Add 'phone' field                        ║
║  u, undo       - Undo last action                         ║
║  r, redo       - Redo last undone action                  ║
║  s, status     - Show current status                      ║
║  h, help       - Show this help                           ║
║  q, quit       - Exit                                     ║
╚═══════════════════════════════════════════════════════════╝

> 1
✅ Added 'email' field (checkpoint created automatically)

> status
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    CURRENT STATUS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Current Checkpoint:
   ID: 1
   Name: auto_1234567890
   GTID: server-1:42
   Created: 2025-01-15 10:30:00

📝 Form Fields:
   - email (email)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Troubleshooting

### Error: "server-id is not set"

```
ERROR: The server is not configured as replication master and server-id is not set
```

**Solution:**

1. Add `server-id = 1` to your MySQL config file under `[mysqld]`
2. Restart MySQL
3. Verify: `SHOW VARIABLES LIKE 'server_id';`

### Error: "Binary logging is not enabled"

```
ERROR: Binary logging is not enabled
```

**Solution:**

1. Add `log-bin = mysql-bin` to your MySQL config file
2. Restart MySQL
3. Verify: `SHOW VARIABLES LIKE 'log_bin';`

### Error: "Access denied; you need the REPLICATION SLAVE privilege"

```
ERROR: Access denied; you need (at least one of) the REPLICATION SLAVE privilege(s)
```

**Solution:**

```sql
-- Connect as root:
mysql -u root -p

-- Grant privileges:
GRANT REPLICATION SLAVE ON *.* TO 'checkpoint_user'@'localhost';
GRANT REPLICATION CLIENT ON *.* TO 'checkpoint_user'@'localhost';
FLUSH PRIVILEGES;

-- Verify:
SHOW GRANTS FOR 'checkpoint_user'@'localhost';
```

### Error: "binlog_format is not ROW"

```
WARN: binlog_format is STATEMENT, expected ROW
```

**Solution:**

1. Add `binlog_format = ROW` to your MySQL config
2. Restart MySQL
3. Verify: `SHOW VARIABLES LIKE 'binlog_format';`

### Error: "Cannot find my.cnf"

**Solution:**

```bash
# Find all config files:
mysql --help | grep "Default options" -A 1

# Or search for it:
sudo find / -name my.cnf 2>/dev/null

# Create one if it doesn't exist:
sudo nano /etc/mysql/my.cnf
```

### Error: "Table 'checkpoint_system.checkpoints' doesn't exist"

**Solution:**

```bash
# Run schema creation:
mysql -u checkpoint_user -p < checkpoint-lib/src/main/resources/schema.sql

# Or verify database exists:
mysql -u checkpoint_user -p -e "SHOW DATABASES LIKE 'checkpoint_system';"
```

### Error: "Communications link failure"

```
ERROR: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
```

**Solution:**

1. Check MySQL is running: `sudo systemctl status mysql`
2. Check port is correct: `mysql -u root -p -h localhost -P 3306`
3. Check firewall: `sudo ufw allow 3306`

### Error: "Maven build failed"

```
ERROR: Failed to execute goal on project checkpoint-lib
```

**Solution:**

```bash
# Check Java version:
java -version  # Should be 11 or higher

# Check Maven version:
mvn -version   # Should be 3.6+

# Clean and rebuild:
mvn clean install -U
```

### Performance Issues

If the system is slow:

1. **Check binlog size:**
   ```sql
   SHOW BINARY LOGS;
   ```

2. **Purge old binlogs:**
   ```sql
   PURGE BINARY LOGS TO 'mysql-bin.000010';
   ```

3. **Adjust retention:**
   ```ini
   expire_logs_days = 3  # Instead of 7
   ```

---

## Production Deployment

### 1. Security Hardening

```sql
-- Create dedicated user with minimal privileges
CREATE USER 'checkpoint_prod'@'localhost' IDENTIFIED BY 'strong_password';

-- Grant only required privileges
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.*
TO 'checkpoint_prod'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE
ON checkpoint_system.* TO 'checkpoint_prod'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE
ON app_production.* TO 'checkpoint_prod'@'localhost';

FLUSH PRIVILEGES;
```

### 2. Configure Retention

```ini
# my.cnf
[mysqld]
# Adjust based on your needs
expire_logs_days = 7              # Keep binlogs for 7 days
max_binlog_size = 100M            # Rotate at 100MB
```

### 3. Monitoring

```sql
-- Monitor binlog size
SELECT
    ROUND(SUM(file_size)/1024/1024, 2) AS binlog_size_mb
FROM information_schema.files
WHERE file_name LIKE 'mysql-bin%';

-- Monitor checkpoint growth
SELECT
    COUNT(*) as checkpoint_count,
    ROUND(SUM(LENGTH(before_image) + LENGTH(after_image))/1024/1024, 2) as events_size_mb
FROM checkpoint_system.checkpoint_transaction_events;
```

### 4. Backup Strategy

```bash
# Backup both databases
mysqldump -u root -p checkpoint_system > checkpoint_system_backup.sql
mysqldump -u root -p app_production > app_production_backup.sql

# Backup binlog position
mysql -u root -p -e "SHOW MASTER STATUS" > binlog_position.txt
```

---

## Quick Reference

### MySQL Config Location

| OS | Default Location |
|----|------------------|
| Ubuntu/Debian | `/etc/mysql/mysql.conf.d/mysqld.cnf` |
| RedHat/CentOS | `/etc/my.cnf` |
| macOS (Homebrew) | `/usr/local/etc/my.cnf` |
| Windows | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |

### Essential Configurations

```ini
[mysqld]
server-id = 1
log-bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
gtid_mode = ON
enforce_gtid_consistency = ON
```

### Essential Permissions

```sql
GRANT REPLICATION SLAVE ON *.* TO 'user'@'localhost';
GRANT REPLICATION CLIENT ON *.* TO 'user'@'localhost';
GRANT ALL ON checkpoint_system.* TO 'user'@'localhost';
```

### Verification Commands

```sql
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';
SHOW VARIABLES LIKE 'binlog_row_image';
SHOW VARIABLES LIKE 'server_id';
SHOW MASTER STATUS;
SHOW BINARY LOGS;
```

---

## Getting Help

If you encounter issues:

1. Check the [Troubleshooting](#troubleshooting) section above
2. Review MySQL error log:
   - Linux: `/var/log/mysql/error.log`
   - macOS: `/usr/local/var/mysql/*.err`
   - Windows: `C:\ProgramData\MySQL\MySQL Server 8.0\Data\*.err`

3. Enable debug logging:
   ```bash
   # Add to logback.xml:
   <logger name="com.checkpoint" level="DEBUG" />
   ```

4. Verify each step completed successfully before proceeding

---

**You're now ready to use the MySQL Checkpoint System!** 🎉

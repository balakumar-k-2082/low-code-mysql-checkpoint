# Low-Code Application Platform

A visual application builder with MySQL transaction-based checkpoint system for undo/redo functionality.

## Features

### Platform Features
- 📱 **Visual App Builder**: Create applications without writing code
- 📝 **Form Builder**: Drag-and-drop form designer with field types
- 📊 **Auto Reports**: Automatic report generation with sort & filter
- 🚀 **Runtime Mode**: Deploy and run your apps instantly
- ⏪ **Undo/Redo**: Transaction-based undo/redo powered by MySQL binlog
- 📜 **Activity Timeline**: See all changes and revert to any state
- 🔄 **Multi-tenancy**: One schema per app for isolation

### Field Types Supported
- Text (single line)
- Email
- Phone
- Textarea (multiline)
- Checkbox
- Dropdown
- Number
- Date

### Checkpoint Integration
- Automatic checkpoint creation for every database transaction
- Per-user undo/redo stacks
- Activity timeline showing all changes
- Click any activity to revert to that state
- Schema-based isolation (each app gets own database)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Frontend (React)                       │
│  Landing Page | App Editor | Form Builder | Runtime     │
└──────────────────────┬──────────────────────────────────┘
                       │ REST API
┌──────────────────────┴──────────────────────────────────┐
│             Spring Boot Backend                          │
│  AppService | FormService | FieldService | RecordService│
│  CheckpointService (integrates checkpoint-lib)          │
└──────────────────────┬──────────────────────────────────┘
                       │
         ┌─────────────┴──────────────┐
         │                             │
    ┌────┴────┐                  ┌────┴────┐
    │  MySQL  │                  │ Binlog  │
    │lowcode_system               │ Capture │
    │  apps table                 │ per app │
    │                             │         │
    │ Per-app databases:          └─────────┘
    │  app_{id}/                       │
    │    forms                         │
    │    fields                        │
    │    field_properties              │
    │    reports                       │
    │    records                       │
    └──────────────────────────────────┘
                       │
         ┌─────────────┴──────────────┐
         │   Checkpoint System         │
         │  checkpoint_system/         │
         │    checkpoints              │
         │    checkpoint_transaction_events
         │    user_checkpoint_position │
         │    apps (registry)          │
         └─────────────────────────────┘
```

## Prerequisites

- Java 11+
- Maven 3.6+
- MySQL 5.7+ or 8.0+
- Node.js 16+ (for frontend)
- npm or yarn

## MySQL Setup

### 1. Configure MySQL for Checkpoint System

Edit your MySQL configuration file:

**Linux**: `/etc/mysql/my.cnf` or `/etc/my.cnf`
**macOS**: `/usr/local/etc/my.cnf` or `/opt/homebrew/etc/my.cnf`
**Windows**: `C:\ProgramData\MySQL\MySQL Server X.X\my.ini`

Add these settings:

```ini
[mysqld]
# Required for checkpoint system
server-id = 1                    # MANDATORY: Any unique number
log_bin = mysql-bin              # Enable binary logging
binlog_format = ROW              # Use ROW format (not STATEMENT)
binlog_row_image = FULL          # Capture complete before/after images
expire_logs_days = 7             # Keep binlogs for 7 days
max_binlog_size = 100M           # Rotate at 100MB

# Character set
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
```

### 2. Restart MySQL

```bash
# Linux
sudo systemctl restart mysql

# macOS
brew services restart mysql

# Windows
net stop MySQL
net start MySQL
```

### 3. Create Database and User

```sql
-- Connect to MySQL
mysql -u root -p

-- Create checkpoint system database
CREATE DATABASE IF NOT EXISTS checkpoint_system;

-- Create low-code system database
CREATE DATABASE IF NOT EXISTS lowcode_system;

-- Grant permissions (replace 'password' with secure password)
GRANT ALL PRIVILEGES ON checkpoint_system.* TO 'root'@'localhost';
GRANT ALL PRIVILEGES ON lowcode_system.* TO 'root'@'localhost';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### 4. Initialize Schema

```bash
# Initialize checkpoint system schema
mysql -u root -p checkpoint_system < ../checkpoint-lib/src/main/resources/schema.sql

# Initialize low-code platform schema
mysql -u root -p lowcode_system < schema.sql
```

## Backend Setup

### 1. Configure Application

Edit `src/main/resources/application.properties`:

```properties
# MySQL Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/lowcode_system
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

# Checkpoint Configuration
checkpoint.mysql.host=localhost
checkpoint.mysql.port=3306
checkpoint.mysql.user=root
checkpoint.mysql.password=YOUR_PASSWORD
```

### 2. Build and Run

```bash
# From lowcode-platform directory
mvn clean package -DskipTests

# Run the backend
java -jar target/lowcode-platform-1.0-SNAPSHOT.jar
```

Backend will start on http://localhost:8080

### 3. Verify Backend

```bash
curl http://localhost:8080/api/health
# Should return: {"status":"UP","service":"Low-Code Platform","version":"1.0.0"}
```

## Frontend Setup

### 1. Install Dependencies

```bash
cd frontend
npm install
```

### 2. Run Development Server

```bash
npm run dev
```

Frontend will start on http://localhost:3000

## Usage

### 1. Create an Application

1. Open http://localhost:3000
2. Click "Create App"
3. Enter app name and description
4. Click "Create App"

### 2. Build Forms in Editor Mode

1. Click on your app to open the editor
2. Click "Create Form" to add a new form
3. Add fields:
   - Click "Add Field"
   - Select field type (text, email, phone, etc.)
   - Configure properties (label, required, placeholder, etc.)
   - Click "Save"

4. Each form automatically gets a default report

### 3. Use Undo/Redo

- **Undo Button**: Revert the last change
- **Redo Button**: Reapply an undone change
- **Activities Panel**: Click to see all changes, click any item to revert to that state

### 4. Open App (Runtime Mode)

1. Click "Open App" button in top right
2. Select a form
3. View records in the report (with sort/filter)
4. Add new records by clicking "Add Record"
5. Edit or delete existing records

### 5. Configure Reports

1. In editor mode, click on a report
2. Configure sorting:
   - Add sort rules (field, direction)
3. Configure filters:
   - Add filter rules (field, operator, value)

## REST API Endpoints

### Apps
- `GET /api/apps` - List all apps
- `POST /api/apps` - Create app
- `GET /api/apps/{appId}` - Get app details
- `PUT /api/apps/{appId}` - Update app
- `DELETE /api/apps/{appId}` - Delete app

### Forms
- `GET /api/apps/{appId}/forms` - List forms
- `POST /api/apps/{appId}/forms` - Create form
- `GET /api/apps/{appId}/forms/{formId}` - Get form
- `PUT /api/apps/{appId}/forms/{formId}` - Update form
- `DELETE /api/apps/{appId}/forms/{formId}` - Delete form

### Fields
- `GET /api/apps/{appId}/forms/{formId}/fields` - List fields
- `POST /api/apps/{appId}/forms/{formId}/fields` - Create field
- `PUT /api/apps/{appId}/fields/{fieldId}` - Update field
- `DELETE /api/apps/{appId}/fields/{fieldId}` - Delete field

### Records
- `GET /api/apps/{appId}/forms/{formId}/records` - List records
- `POST /api/apps/{appId}/forms/{formId}/records` - Create record
- `PUT /api/apps/{appId}/records/{recordId}` - Update record
- `DELETE /api/apps/{appId}/records/{recordId}` - Delete record

### Checkpoints (Undo/Redo)
- `POST /api/apps/{appId}/checkpoints/undo?userId={userId}` - Undo
- `POST /api/apps/{appId}/checkpoints/redo?userId={userId}` - Redo
- `GET /api/apps/{appId}/checkpoints/activities?userId={userId}` - Get activities
- `POST /api/apps/{appId}/checkpoints/revert/{checkpointId}?userId={userId}` - Revert to checkpoint

## How Checkpoints Work

1. **Automatic Tracking**: Every database change in editor mode is automatically tracked as a transaction

2. **Transaction Boundaries**: Changes are grouped by MySQL transactions:
   - Adding a field = 1 checkpoint
   - Making field mandatory + adding validation = 1 checkpoint (if in same transaction)

3. **Undo/Redo**:
   - Undo: Reverts the last transaction using stored before/after images
   - Redo: Reapplies an undone transaction
   - Revert: Jumps to any previous state by undoing multiple transactions

4. **Activity Timeline**:
   - Shows all checkpoints for the current user
   - Displays what changed in each checkpoint
   - Click to revert to that exact state

5. **Per-User Tracking**:
   - Each user has their own undo/redo stack
   - Changes are isolated by user ID
   - Current position tracked independently

## Example Workflow

### Building a CRM App

```javascript
// 1. Create App
POST /api/apps
{
  "name": "CRM System",
  "description": "Customer management",
  "createdBy": "john@example.com"
}

// 2. Create "Contacts" Form
POST /api/apps/{appId}/forms
{
  "name": "Contacts",
  "description": "Customer contacts"
}

// 3. Add "Name" Field (Checkpoint 1)
POST /api/apps/{appId}/forms/{formId}/fields
{
  "name": "name",
  "label": "Full Name",
  "fieldType": "text",
  "isRequired": true
}

// 4. Add "Email" Field (Checkpoint 2)
POST /api/apps/{appId}/forms/{formId}/fields
{
  "name": "email",
  "label": "Email Address",
  "fieldType": "email",
  "isRequired": true
}

// 5. Add "Phone" Field (Checkpoint 3)
POST /api/apps/{appId}/forms/{formId}/fields
{
  "name": "phone",
  "label": "Phone Number",
  "fieldType": "phone"
}

// 6. Oops! Undo last field (removes phone field)
POST /api/apps/{appId}/checkpoints/undo?userId=john@example.com

// 7. View activities
GET /api/apps/{appId}/checkpoints/activities?userId=john@example.com
// Returns: [Checkpoint 1: Added name field, Checkpoint 2: Added email field]

// 8. Redo (adds phone field back)
POST /api/apps/{appId}/checkpoints/redo?userId=john@example.com
```

## Troubleshooting

### Backend won't start
- Check MySQL is running: `mysql -u root -p`
- Verify binlog is enabled: `SHOW VARIABLES LIKE 'log_bin';`
- Check permissions: User needs REPLICATION SLAVE privilege

### Frontend can't connect to backend
- Verify backend is running on port 8080
- Check browser console for CORS errors
- Ensure `cors.allowed.origins` includes `http://localhost:3000`

### Undo/Redo not working
- Check that user is set: `POST /api/apps/{appId}/checkpoints/user`
- Verify checkpoints are being created: `GET /api/apps/{appId}/checkpoints/activities`
- Check MySQL binlog format: `SHOW VARIABLES LIKE 'binlog_format';` (should be ROW)

### No checkpoints appearing
- Ensure server-id is set in my.cnf
- Restart MySQL after config changes
- Check binlog is capturing: `SHOW BINARY LOGS;`

## Development

### Project Structure

```
lowcode-platform/
├── src/main/java/com/lowcode/
│   ├── LowCodePlatformApplication.java   # Main application
│   ├── model/
│   │   └── App.java                      # App entity
│   ├── dto/
│   │   ├── FormDTO.java                  # Form data transfer object
│   │   ├── FieldDTO.java                 # Field DTO
│   │   ├── ReportDTO.java                # Report DTO
│   │   ├── RecordDTO.java                # Record DTO
│   │   └── CheckpointActivityDTO.java    # Activity DTO
│   ├── repository/
│   │   └── AppRepository.java            # App repository
│   ├── service/
│   │   ├── AppService.java               # App business logic
│   │   ├── FormService.java              # Form operations
│   │   ├── FieldService.java             # Field operations
│   │   ├── ReportService.java            # Report operations
│   │   ├── RecordService.java            # Record operations
│   │   ├── CheckpointService.java        # Checkpoint integration
│   │   └── DatabaseService.java          # Per-app DB management
│   └── controller/
│       ├── AppController.java            # App REST API
│       ├── FormController.java           # Form/Field/Record API
│       ├── CheckpointController.java     # Undo/Redo API
│       └── HealthController.java         # Health check
├── frontend/
│   ├── src/
│   │   ├── App.jsx                       # Main component
│   │   ├── api.js                        # API client
│   │   ├── pages/
│   │   │   ├── LandingPage.jsx           # App list page
│   │   │   ├── AppEditor.jsx             # Editor mode
│   │   │   └── AppRuntime.jsx            # Runtime mode
│   │   └── components/
│   │       ├── FormBuilder.jsx           # Form builder
│   │       ├── FieldEditor.jsx           # Field editor
│   │       ├── ReportViewer.jsx          # Report viewer
│   │       └── ActivitiesPanel.jsx       # Activities timeline
│   └── package.json
├── schema.sql                            # Database schema
├── pom.xml                               # Maven config
└── README.md                             # This file
```

### Adding New Field Types

1. Add field type constant to `FieldDTO.java`:
```java
public static final String TYPE_NEW_FIELD = "newfield";
```

2. Update frontend field type selector in `FieldEditor.jsx`

3. Add rendering logic in `FormRenderer.jsx` (runtime mode)

## Production Deployment

### MySQL Configuration

- Use strong passwords
- Enable SSL for database connections
- Set appropriate `expire_logs_days` based on disk space
- Monitor binlog size growth

### Application Configuration

```properties
# Production settings
server.port=8080
spring.jpa.show-sql=false
logging.level.com.lowcode=INFO

# Security (add Spring Security)
# Add authentication/authorization
# Add JWT tokens for API
```

### Scaling Considerations

- Each app gets its own database (good for isolation)
- Checkpoint system per app (independent binlog capture)
- Can deploy multiple backend instances with load balancer
- Frontend is static, can use CDN

## License

MIT License

## Support

For issues and questions:
- Check SETUP_GUIDE.md for MySQL configuration
- Check CLAUDE.md for using Claude Code assistant
- Check QUICK_REFERENCE.md for common commands

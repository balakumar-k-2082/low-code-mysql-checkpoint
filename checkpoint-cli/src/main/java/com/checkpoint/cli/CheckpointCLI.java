package com.checkpoint.cli;

import com.checkpoint.manager.CheckpointManager;
import com.checkpoint.manager.UndoRedoManager;
import com.checkpoint.model.Checkpoint;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Scanner;

/**
 * CLI tool for demonstrating checkpoint functionality
 */
public class CheckpointCLI {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointCLI.class);

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "password";
    private static final String DEFAULT_APP_ID = "demo_app";
    private static final String DEFAULT_SCHEMA = "app_demo";

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("   MySQL Transaction-Based Checkpoint System - CLI Demo   ");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        try {
            // Get configuration from user or use defaults
            Scanner scanner = new Scanner(System.in);

            System.out.print("MySQL Host [" + DEFAULT_HOST + "]: ");
            String host = scanner.nextLine().trim();
            if (host.isEmpty()) host = DEFAULT_HOST;

            System.out.print("MySQL Port [" + DEFAULT_PORT + "]: ");
            String portStr = scanner.nextLine().trim();
            int port = portStr.isEmpty() ? DEFAULT_PORT : Integer.parseInt(portStr);

            System.out.print("MySQL User [" + DEFAULT_USER + "]: ");
            String user = scanner.nextLine().trim();
            if (user.isEmpty()) user = DEFAULT_USER;

            System.out.print("MySQL Password: ");
            String password = scanner.nextLine().trim();
            if (password.isEmpty()) password = DEFAULT_PASSWORD;

            System.out.println("\n Connecting to MySQL...");

            // Create datasource
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setURL("jdbc:mysql://" + host + ":" + port + "/checkpoint_system");
            dataSource.setUser(user);
            dataSource.setPassword(password);

            // Initialize demo schema
            System.out.println("📦 Setting up demo schema...");
            setupDemoSchema(dataSource, DEFAULT_SCHEMA);

            // Initialize checkpoint manager
            System.out.println("🔧 Initializing checkpoint system...");
            CheckpointManager checkpointManager = new CheckpointManager(
                dataSource,
                DEFAULT_APP_ID,
                DEFAULT_SCHEMA,
                host,
                port,
                user,
                password
            );

            checkpointManager.initialize();

            // Initialize undo/redo manager
            UndoRedoManager undoRedoManager = new UndoRedoManager(
                dataSource,
                checkpointManager,
                DEFAULT_APP_ID,
                DEFAULT_SCHEMA
            );

            System.out.println("✅ Checkpoint system initialized!\n");

            // Run interactive demo
            runInteractiveDemo(scanner, dataSource, checkpointManager, undoRedoManager);

            // Cleanup
            checkpointManager.stop();
            System.out.println("\n👋 Goodbye!");

        } catch (Exception e) {
            logger.error("Error in CLI", e);
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Setup demo schema with form_fields table
     */
    private static void setupDemoSchema(MysqlDataSource dataSource, String schemaName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create schema
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + schemaName);

            // Create form_fields table
            stmt.executeUpdate("USE " + schemaName);
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS form_fields (" +
                "  id INT PRIMARY KEY AUTO_INCREMENT," +
                "  field_name VARCHAR(255) NOT NULL," +
                "  field_type VARCHAR(50) NOT NULL," +
                "  is_required BOOLEAN DEFAULT FALSE," +
                "  properties JSON" +
                ") ENGINE=InnoDB"
            );

            // Create validation_rules table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS validation_rules (" +
                "  id INT PRIMARY KEY AUTO_INCREMENT," +
                "  field_id INT NOT NULL," +
                "  rule_type VARCHAR(50) NOT NULL," +
                "  rule_config JSON" +
                ") ENGINE=InnoDB"
            );

            System.out.println("✅ Demo schema ready: " + schemaName);
        }
    }

    /**
     * Run interactive demo
     */
    private static void runInteractiveDemo(Scanner scanner, MysqlDataSource dataSource,
                                           CheckpointManager checkpointManager,
                                           UndoRedoManager undoRedoManager) {

        String userId = "demo_user";

        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                    DEMO SCENARIO                          ║");
        System.out.println("║  You are building a form in a low-code platform.          ║");
        System.out.println("║  Each action you take creates a transaction checkpoint.   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");

        printHelp();

        while (true) {
            System.out.print("\n> ");
            String command = scanner.nextLine().trim().toLowerCase();

            try {
                if (command.isEmpty()) {
                    continue;
                }

                switch (command) {
                    case "1":
                    case "add":
                        addEmailField(dataSource);
                        System.out.println("✅ Added 'email' field (checkpoint created automatically)");
                        break;

                    case "2":
                    case "mandatory":
                        makeEmailMandatory(dataSource);
                        System.out.println("✅ Made 'email' field mandatory (checkpoint created)");
                        break;

                    case "3":
                    case "phone":
                        addPhoneField(dataSource);
                        System.out.println("✅ Added 'phone' field (checkpoint created)");
                        break;

                    case "u":
                    case "undo":
                        undoRedoManager.undo(userId);
                        System.out.println("↶ UNDO completed");
                        break;

                    case "r":
                    case "redo":
                        undoRedoManager.redo(userId);
                        System.out.println("↷ REDO completed");
                        break;

                    case "s":
                    case "status":
                        showStatus(dataSource, checkpointManager, userId);
                        break;

                    case "h":
                    case "help":
                        printHelp();
                        break;

                    case "q":
                    case "quit":
                    case "exit":
                        return;

                    default:
                        System.out.println("❓ Unknown command. Type 'help' for commands.");
                }

            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                logger.error("Command execution failed", e);
            }
        }
    }

    /**
     * Action 1: Add email field
     */
    private static void addEmailField(MysqlDataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("USE app_demo");

                // Check if already exists
                stmt.executeUpdate("DELETE FROM form_fields WHERE field_name = 'email'");

                // Insert email field
                stmt.executeUpdate(
                    "INSERT INTO form_fields (field_name, field_type, is_required, properties) " +
                    "VALUES ('email', 'email', FALSE, '{\"placeholder\":\"Enter email\"}')"
                );

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Give binlog time to process
        Thread.sleep(1000);
    }

    /**
     * Action 2: Make email mandatory
     */
    private static void makeEmailMandatory(MysqlDataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("USE app_demo");

                // Update field
                stmt.executeUpdate(
                    "UPDATE form_fields SET is_required = TRUE " +
                    "WHERE field_name = 'email'"
                );

                // Add validation rule
                stmt.executeUpdate(
                    "INSERT INTO validation_rules (field_id, rule_type, rule_config) " +
                    "SELECT id, 'required', '{\"message\":\"Email is required\"}' " +
                    "FROM form_fields WHERE field_name = 'email'"
                );

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        Thread.sleep(1000);
    }

    /**
     * Action 3: Add phone field
     */
    private static void addPhoneField(MysqlDataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("USE app_demo");

                // Check if already exists
                stmt.executeUpdate("DELETE FROM form_fields WHERE field_name = 'phone'");

                // Insert phone field
                stmt.executeUpdate(
                    "INSERT INTO form_fields (field_name, field_type, is_required, properties) " +
                    "VALUES ('phone', 'tel', FALSE, '{\"placeholder\":\"Enter phone number\"}')"
                );

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        Thread.sleep(1000);
    }

    /**
     * Show current status
     */
    private static void showStatus(MysqlDataSource dataSource, CheckpointManager checkpointManager,
                                   String userId) throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("                    CURRENT STATUS                        ");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Show current checkpoint
        Checkpoint current = checkpointManager.getCurrentCheckpoint(userId);
        if (current != null) {
            System.out.println("📍 Current Checkpoint:");
            System.out.println("   ID: " + current.getId());
            System.out.println("   Name: " + current.getName());
            System.out.println("   GTID: " + current.getGtid());
            System.out.println("   Created: " + current.getCreatedAt());
        } else {
            System.out.println("📍 No checkpoints yet");
        }

        // Show form fields
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeQuery("USE app_demo");
            var rs = stmt.executeQuery(
                "SELECT field_name, field_type, is_required FROM form_fields"
            );

            System.out.println("\n📝 Form Fields:");
            while (rs.next()) {
                String name = rs.getString("field_name");
                String type = rs.getString("field_type");
                boolean required = rs.getBoolean("is_required");

                System.out.printf("   - %s (%s) %s%n",
                                name, type, required ? "[REQUIRED]" : "");
            }
            rs.close();
        }

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
    }

    /**
     * Print help
     */
    private static void printHelp() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                       COMMANDS                            ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  1, add        - Add 'email' field                        ║");
        System.out.println("║  2, mandatory  - Make 'email' field mandatory             ║");
        System.out.println("║  3, phone      - Add 'phone' field                        ║");
        System.out.println("║  u, undo       - Undo last action                         ║");
        System.out.println("║  r, redo       - Redo last undone action                  ║");
        System.out.println("║  s, status     - Show current status                      ║");
        System.out.println("║  h, help       - Show this help                           ║");
        System.out.println("║  q, quit       - Exit                                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }
}

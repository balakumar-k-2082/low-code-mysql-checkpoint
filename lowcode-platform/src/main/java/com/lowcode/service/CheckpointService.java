package com.lowcode.service;

import com.checkpoint.manager.CheckpointManager;
import com.checkpoint.manager.UndoRedoManager;
import com.checkpoint.model.Checkpoint;
import com.lowcode.dto.CheckpointActivityDTO;
import com.mysql.cj.jdbc.MysqlDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing checkpoint system integration
 * Maintains CheckpointManager instances for each app
 */
@Service
@Slf4j
public class CheckpointService {

    @Value("${checkpoint.mysql.host}")
    private String mysqlHost;

    @Value("${checkpoint.mysql.port}")
    private int mysqlPort;

    @Value("${checkpoint.mysql.user}")
    private String mysqlUser;

    @Value("${checkpoint.mysql.password}")
    private String mysqlPassword;

    @Value("${checkpoint.enabled:true}")
    private boolean checkpointEnabled;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    // Map of appId -> CheckpointManager
    private final Map<String, CheckpointManager> checkpointManagers = new ConcurrentHashMap<>();

    // Map of appId -> UndoRedoManager
    private final Map<String, UndoRedoManager> undoRedoManagers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (checkpointEnabled) {
            log.info("Checkpoint system enabled");
        } else {
            log.warn("Checkpoint system disabled");
        }
    }

    /**
     * Initialize checkpoint system for an app
     */
    public void initializeCheckpointForApp(String appId, String schemaName) throws SQLException {
        if (!checkpointEnabled) {
            log.info("Checkpoint system disabled, skipping initialization for app: {}", appId);
            return;
        }

        log.info("Initializing checkpoint system for app: {} schema: {}", appId, schemaName);

        // Create datasource for checkpoint system
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/checkpoint_system");
        dataSource.setUser(datasourceUsername);
        dataSource.setPassword(datasourcePassword);

        // Create checkpoint manager
        CheckpointManager checkpointManager = new CheckpointManager(
            dataSource,
            appId,
            schemaName,
            mysqlHost,
            mysqlPort,
            mysqlUser,
            mysqlPassword
        );

        // Initialize and start
        checkpointManager.initialize();

        // Create undo/redo manager
        UndoRedoManager undoRedoManager = new UndoRedoManager(
            dataSource,
            checkpointManager,
            appId,
            schemaName
        );

        // Store managers
        checkpointManagers.put(appId, checkpointManager);
        undoRedoManagers.put(appId, undoRedoManager);

        log.info("Checkpoint system initialized for app: {}", appId);
    }

    /**
     * Set active user for checkpoint tracking
     */
    public void setActiveUser(String appId, String userId) {
        CheckpointManager manager = checkpointManagers.get(appId);
        if (manager != null) {
            manager.setActiveUser(userId);
            log.debug("Set active user for app {}: {}", appId, userId);
        }
    }

    /**
     * Perform undo operation
     */
    public void undo(String appId, String userId) throws SQLException {
        UndoRedoManager manager = undoRedoManagers.get(appId);
        if (manager == null) {
            throw new IllegalStateException("Checkpoint system not initialized for app: " + appId);
        }

        log.info("Performing undo for app: {} user: {}", appId, userId);
        manager.undo(userId);
    }

    /**
     * Perform redo operation
     */
    public void redo(String appId, String userId) throws SQLException {
        UndoRedoManager manager = undoRedoManagers.get(appId);
        if (manager == null) {
            throw new IllegalStateException("Checkpoint system not initialized for app: " + appId);
        }

        log.info("Performing redo for app: {} user: {}", appId, userId);
        manager.redo(userId);
    }

    /**
     * Get checkpoint activities for display in UI
     */
    public List<CheckpointActivityDTO> getActivities(String appId, String userId) throws SQLException {
        CheckpointManager manager = checkpointManagers.get(appId);
        if (manager == null) {
            return new ArrayList<>();
        }

        // Get current checkpoint
        Checkpoint currentCheckpoint = manager.getCurrentCheckpoint(userId);
        Long currentId = currentCheckpoint != null ? currentCheckpoint.getId() : null;

        // Get all checkpoints for user
        List<Checkpoint> checkpoints = manager.getCheckpointHistory(userId, 50);

        List<CheckpointActivityDTO> activities = new ArrayList<>();
        for (Checkpoint checkpoint : checkpoints) {
            CheckpointActivityDTO activity = new CheckpointActivityDTO();
            activity.setId(checkpoint.getId());
            activity.setName(checkpoint.getName());
            activity.setGtid(checkpoint.getGtid());
            activity.setCreatedAt(checkpoint.getCreatedAt().toLocalDateTime());
            activity.setIsCurrent(checkpoint.getId() == currentId);

            // Load transaction events for this checkpoint
            var events = manager.loadTransactionEvents(checkpoint.getId());
            List<CheckpointActivityDTO.ChangeDetail> changes = new ArrayList<>();

            for (var event : events) {
                CheckpointActivityDTO.ChangeDetail change = new CheckpointActivityDTO.ChangeDetail();
                change.setEventType(event.getEventType());
                change.setTableName(event.getTableName());
                change.setDescription(generateChangeDescription(event));
                changes.add(change);
            }

            activity.setChanges(changes);
            activities.add(activity);
        }

        return activities;
    }

    /**
     * Revert to a specific checkpoint
     */
    public void revertToCheckpoint(String appId, String userId, Long checkpointId) throws SQLException {
        UndoRedoManager manager = undoRedoManagers.get(appId);
        CheckpointManager checkpointManager = checkpointManagers.get(appId);

        if (manager == null || checkpointManager == null) {
            throw new IllegalStateException("Checkpoint system not initialized for app: " + appId);
        }

        log.info("Reverting to checkpoint: {} for app: {} user: {}", checkpointId, appId, userId);

        // Get current and target checkpoints
        Checkpoint current = checkpointManager.getCurrentCheckpoint(userId);
        Checkpoint target = checkpointManager.getCheckpointById(checkpointId);

        if (current == null || target == null) {
            throw new IllegalStateException("Invalid checkpoint state");
        }

        // Determine if we need to undo or redo
        // For now, implement simple undo-until-target logic
        while (true) {
            Checkpoint currentPos = checkpointManager.getCurrentCheckpoint(userId);
            if (currentPos.getId() == checkpointId) {
                break;
            }

            // Try undo
            manager.undo(userId);
        }

        log.info("Reverted to checkpoint: {}", checkpointId);
    }

    /**
     * Stop checkpoint system for an app
     */
    public void stopCheckpointForApp(String appId) {
        CheckpointManager manager = checkpointManagers.remove(appId);
        undoRedoManagers.remove(appId);

        if (manager != null) {
            try {
                manager.stop();
                log.info("Stopped checkpoint system for app: {}", appId);
            } catch (Exception e) {
                log.error("Error stopping checkpoint system for app: {}", appId, e);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Stopping all checkpoint systems...");
        for (Map.Entry<String, CheckpointManager> entry : checkpointManagers.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception e) {
                log.error("Error stopping checkpoint system for app: {}", entry.getKey(), e);
            }
        }
        checkpointManagers.clear();
        undoRedoManagers.clear();
    }

    /**
     * Generate human-readable description of change
     */
    private String generateChangeDescription(com.checkpoint.model.TransactionEvent event) {
        switch (event.getEventType()) {
            case "INSERT":
                return "Created " + event.getTableName() + " record";
            case "UPDATE":
                return "Updated " + event.getTableName() + " record";
            case "DELETE":
                return "Deleted " + event.getTableName() + " record";
            default:
                return event.getEventType() + " on " + event.getTableName();
        }
    }
}

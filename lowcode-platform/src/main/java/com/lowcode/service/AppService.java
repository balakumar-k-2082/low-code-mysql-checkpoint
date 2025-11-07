package com.lowcode.service;

import com.lowcode.model.App;
import com.lowcode.repository.AppRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing apps
 */
@Service
@Slf4j
public class AppService {

    private final AppRepository appRepository;
    private final DatabaseService databaseService;
    private final CheckpointService checkpointService;

    public AppService(AppRepository appRepository,
                     DatabaseService databaseService,
                     CheckpointService checkpointService) {
        this.appRepository = appRepository;
        this.databaseService = databaseService;
        this.checkpointService = checkpointService;
    }

    /**
     * Get all apps
     */
    public List<App> getAllApps() {
        return appRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get app by ID
     */
    public App getAppById(String id) {
        return appRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("App not found: " + id));
    }

    /**
     * Create a new app
     */
    @Transactional
    public App createApp(App app) {
        log.info("Creating app: {}", app.getName());

        // Save app metadata
        App savedApp = appRepository.save(app);

        // Create app database
        databaseService.createAppDatabase(savedApp.getSchemaName());

        // Initialize checkpoint system for this app
        try {
            checkpointService.initializeCheckpointForApp(
                savedApp.getId(),
                savedApp.getSchemaName()
            );
        } catch (Exception e) {
            log.error("Failed to initialize checkpoint system for app: {}", savedApp.getId(), e);
            // Continue anyway - checkpoint system is optional
        }

        log.info("App created successfully: {} with schema: {}",
                savedApp.getId(), savedApp.getSchemaName());

        return savedApp;
    }

    /**
     * Update app metadata
     */
    @Transactional
    public App updateApp(String id, App app) {
        App existing = getAppById(id);
        existing.setName(app.getName());
        existing.setDescription(app.getDescription());
        existing.setIcon(app.getIcon());
        return appRepository.save(existing);
    }

    /**
     * Delete app and its database
     */
    @Transactional
    public void deleteApp(String id) {
        App app = getAppById(id);
        log.warn("Deleting app: {} with schema: {}", id, app.getSchemaName());

        // Stop checkpoint system
        try {
            checkpointService.stopCheckpointForApp(id);
        } catch (Exception e) {
            log.error("Failed to stop checkpoint system for app: {}", id, e);
        }

        // Delete app database
        databaseService.deleteAppDatabase(app.getSchemaName());

        // Delete app metadata
        appRepository.delete(app);

        log.info("App deleted successfully: {}", id);
    }
}

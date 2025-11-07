package com.lowcode.controller;

import com.lowcode.model.App;
import com.lowcode.service.AppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for App operations
 */
@RestController
@RequestMapping("/api/apps")
@Slf4j
public class AppController {

    private final AppService appService;

    public AppController(AppService appService) {
        this.appService = appService;
    }

    /**
     * Get all apps
     */
    @GetMapping
    public ResponseEntity<List<App>> getAllApps() {
        return ResponseEntity.ok(appService.getAllApps());
    }

    /**
     * Get app by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<App> getAppById(@PathVariable String id) {
        return ResponseEntity.ok(appService.getAppById(id));
    }

    /**
     * Create new app
     */
    @PostMapping
    public ResponseEntity<App> createApp(@RequestBody App app) {
        log.info("Creating app: {}", app.getName());
        return ResponseEntity.ok(appService.createApp(app));
    }

    /**
     * Update app
     */
    @PutMapping("/{id}")
    public ResponseEntity<App> updateApp(@PathVariable String id, @RequestBody App app) {
        return ResponseEntity.ok(appService.updateApp(id, app));
    }

    /**
     * Delete app
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApp(@PathVariable String id) {
        appService.deleteApp(id);
        return ResponseEntity.noContent().build();
    }
}

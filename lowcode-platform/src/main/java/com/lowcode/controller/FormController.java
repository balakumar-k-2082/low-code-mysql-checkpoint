package com.lowcode.controller;

import com.lowcode.dto.FieldDTO;
import com.lowcode.dto.FormDTO;
import com.lowcode.dto.RecordDTO;
import com.lowcode.dto.ReportDTO;
import com.lowcode.model.App;
import com.lowcode.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Form, Field, Report, and Record operations
 */
@RestController
@RequestMapping("/api/apps/{appId}")
@Slf4j
public class FormController {

    private final AppService appService;
    private final FormService formService;
    private final FieldService fieldService;
    private final ReportService reportService;
    private final RecordService recordService;

    public FormController(AppService appService,
                         FormService formService,
                         FieldService fieldService,
                         ReportService reportService,
                         RecordService recordService) {
        this.appService = appService;
        this.formService = formService;
        this.fieldService = fieldService;
        this.reportService = reportService;
        this.recordService = recordService;
    }

    // =========================================================================
    // FORMS
    // =========================================================================

    /**
     * Get all forms for an app
     */
    @GetMapping("/forms")
    public ResponseEntity<List<FormDTO>> getFormsByApp(@PathVariable String appId) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(formService.getFormsByApp(app.getSchemaName()));
    }

    /**
     * Get form by ID
     */
    @GetMapping("/forms/{formId}")
    public ResponseEntity<FormDTO> getFormById(@PathVariable String appId,
                                               @PathVariable String formId) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(formService.getFormById(app.getSchemaName(), formId));
    }

    /**
     * Create new form
     */
    @PostMapping("/forms")
    public ResponseEntity<FormDTO> createForm(@PathVariable String appId,
                                              @RequestBody FormDTO form) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(formService.createForm(app.getSchemaName(), form));
    }

    /**
     * Update form
     */
    @PutMapping("/forms/{formId}")
    public ResponseEntity<FormDTO> updateForm(@PathVariable String appId,
                                              @PathVariable String formId,
                                              @RequestBody FormDTO form) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(formService.updateForm(app.getSchemaName(), formId, form));
    }

    /**
     * Delete form
     */
    @DeleteMapping("/forms/{formId}")
    public ResponseEntity<Void> deleteForm(@PathVariable String appId,
                                           @PathVariable String formId) {
        App app = appService.getAppById(appId);
        formService.deleteForm(app.getSchemaName(), formId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // FIELDS
    // =========================================================================

    /**
     * Get all fields for a form
     */
    @GetMapping("/forms/{formId}/fields")
    public ResponseEntity<List<FieldDTO>> getFieldsByForm(@PathVariable String appId,
                                                          @PathVariable String formId) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(fieldService.getFieldsByForm(app.getSchemaName(), formId));
    }

    /**
     * Create new field
     */
    @PostMapping("/forms/{formId}/fields")
    public ResponseEntity<FieldDTO> createField(@PathVariable String appId,
                                                @PathVariable String formId,
                                                @RequestBody FieldDTO field) {
        App app = appService.getAppById(appId);
        field.setFormId(formId);
        return ResponseEntity.ok(fieldService.createField(app.getSchemaName(), field));
    }

    /**
     * Update field
     */
    @PutMapping("/fields/{fieldId}")
    public ResponseEntity<FieldDTO> updateField(@PathVariable String appId,
                                                @PathVariable String fieldId,
                                                @RequestBody FieldDTO field) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(fieldService.updateField(app.getSchemaName(), fieldId, field));
    }

    /**
     * Delete field
     */
    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<Void> deleteField(@PathVariable String appId,
                                            @PathVariable String fieldId) {
        App app = appService.getAppById(appId);
        fieldService.deleteField(app.getSchemaName(), fieldId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // REPORTS
    // =========================================================================

    /**
     * Get all reports for a form
     */
    @GetMapping("/forms/{formId}/reports")
    public ResponseEntity<List<ReportDTO>> getReportsByForm(@PathVariable String appId,
                                                            @PathVariable String formId) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(reportService.getReportsByForm(app.getSchemaName(), formId));
    }

    /**
     * Update report
     */
    @PutMapping("/reports/{reportId}")
    public ResponseEntity<ReportDTO> updateReport(@PathVariable String appId,
                                                  @PathVariable String reportId,
                                                  @RequestBody ReportDTO report) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(reportService.updateReport(app.getSchemaName(), reportId, report));
    }

    // =========================================================================
    // RECORDS (Form Data)
    // =========================================================================

    /**
     * Get all records for a form
     */
    @GetMapping("/forms/{formId}/records")
    public ResponseEntity<List<RecordDTO>> getRecordsByForm(@PathVariable String appId,
                                                            @PathVariable String formId,
                                                            @RequestParam(required = false) String reportId) {
        App app = appService.getAppById(appId);

        if (reportId != null) {
            // Get records with report filters/sorting
            ReportDTO report = reportService.getReportById(app.getSchemaName(), reportId);
            return ResponseEntity.ok(recordService.getRecordsByReport(app.getSchemaName(), formId, report));
        } else {
            // Get all records
            return ResponseEntity.ok(recordService.getRecordsByForm(app.getSchemaName(), formId));
        }
    }

    /**
     * Get record by ID
     */
    @GetMapping("/records/{recordId}")
    public ResponseEntity<RecordDTO> getRecordById(@PathVariable String appId,
                                                   @PathVariable String recordId) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(recordService.getRecordById(app.getSchemaName(), recordId));
    }

    /**
     * Create new record
     */
    @PostMapping("/forms/{formId}/records")
    public ResponseEntity<RecordDTO> createRecord(@PathVariable String appId,
                                                  @PathVariable String formId,
                                                  @RequestBody RecordDTO record) {
        App app = appService.getAppById(appId);
        record.setFormId(formId);
        return ResponseEntity.ok(recordService.createRecord(app.getSchemaName(), record));
    }

    /**
     * Update record
     */
    @PutMapping("/records/{recordId}")
    public ResponseEntity<RecordDTO> updateRecord(@PathVariable String appId,
                                                  @PathVariable String recordId,
                                                  @RequestBody RecordDTO record) {
        App app = appService.getAppById(appId);
        return ResponseEntity.ok(recordService.updateRecord(app.getSchemaName(), recordId, record));
    }

    /**
     * Delete record
     */
    @DeleteMapping("/records/{recordId}")
    public ResponseEntity<Void> deleteRecord(@PathVariable String appId,
                                             @PathVariable String recordId) {
        App app = appService.getAppById(appId);
        recordService.deleteRecord(app.getSchemaName(), recordId);
        return ResponseEntity.noContent().build();
    }
}

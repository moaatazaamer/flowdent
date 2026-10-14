package com.flowdent.backend.controller;

import com.flowdent.backend.model.LegacyType;
import com.flowdent.backend.service.LegacyImportService;
import com.flowdent.backend.service.LegacyImportService.BatchView;
import com.flowdent.backend.service.LegacyImportService.Preview;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Import and browse old (pre-system) financial records. ADMIN only (covered by /api/admin/**). */
@RestController
@RequestMapping("/api/admin/legacy")
@RequiredArgsConstructor
public class LegacyController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LegacyImportService service;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowdent-old-records-template.xlsx\"")
                .contentType(XLSX)
                .body(service.template());
    }

    /** Reads the file and reports what it contains. Saves nothing. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "type", required = false) String type) {
        return service.preview(file, type);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchView> importRecords(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "type", required = false) String type,
                                                   @RequestParam(value = "skipInvalid", defaultValue = "false") boolean skipInvalid) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.commit(file, type, skipInvalid));
    }

    @GetMapping("/batches")
    public List<BatchView> batches() {
        return service.batches();
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable Long id) {
        service.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(required = false) LegacyType type,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "200") int limit) {
        return service.records(type, year, q, Math.min(Math.max(limit, 1), 1000));
    }
}

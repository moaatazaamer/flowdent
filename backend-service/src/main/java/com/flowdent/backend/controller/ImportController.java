package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.ImportResult;
import com.flowdent.backend.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
public class ImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelService excelService;

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importProducts(@RequestParam("file") MultipartFile file) {
        return excelService.importProducts(file);
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCategories(@RequestParam("file") MultipartFile file) {
        return excelService.importCategories(file);
    }

    @GetMapping("/template/products")
    public ResponseEntity<byte[]> productTemplate() {
        return xlsx("flowdent-products-template.xlsx", excelService.productTemplate());
    }

    @GetMapping("/template/categories")
    public ResponseEntity<byte[]> categoryTemplate() {
        return xlsx("flowdent-categories-template.xlsx", excelService.categoryTemplate());
    }

    @GetMapping("/export/products")
    public ResponseEntity<byte[]> exportProducts() {
        return xlsx("flowdent-products.xlsx", excelService.exportProducts());
    }

    @GetMapping("/export/categories")
    public ResponseEntity<byte[]> exportCategories() {
        return xlsx("flowdent-categories.xlsx", excelService.exportCategories());
    }

    private static ResponseEntity<byte[]> xlsx(String name, byte[] data) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(XLSX)
                .body(data);
    }
}

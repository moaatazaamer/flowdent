package com.flowdent.backend.controller;

import com.flowdent.backend.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 5MB limit");

        String original = StringUtils.cleanName(file.getOriginalFilename());
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) throw ApiException.badRequest("Only JPG, PNG, GIF or WEBP images are allowed");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) throw ApiException.badRequest("File is not an image");

        String filename = UUID.randomUUID() + "." + ext;
        Files.copy(file.getInputStream(), root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return Map.of("filename", filename, "url", "/api/images/" + filename);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) throws MalformedURLException {
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw ApiException.notFound("Image");
        String type;
        try {
            type = Files.probeContentType(file);
        } catch (IOException e) {
            type = null;
        }
        return ResponseEntity.ok()
                .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(new UrlResource(file.toUri()));
    }

    private static final class StringUtils {
        static String cleanName(String name) {
            if (name == null) return "";
            return Paths.get(name).getFileName().toString();
        }
    }
}

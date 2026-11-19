package com.outputhub.api;

import com.outputhub.domain.Version;
import com.outputhub.storage.StorageService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 제출문서 원본 내려받기: 대시보드·상세 화면의 링크가 이 엔드포인트를 쓴다. */
@RestController
public class VersionDownloadController {

    private final Version.Repository versions;
    private final StorageService storage;

    public VersionDownloadController(Version.Repository versions, StorageService storage) {
        this.versions = versions;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    @GetMapping("/api/versions/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        Version version = versions.findById(id).orElse(null);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = storage.resolve(version.getSubmission().getStoredPath());
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String fileName = version.getSubmission().getFileName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}

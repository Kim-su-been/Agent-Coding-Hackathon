package com.outputhub.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {

    private static final DateTimeFormatter ROUND_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final String OLD_FOLDER = "old";
    private static final int UNIQUE_TOKEN_LENGTH = 8;

    private final Path storageRoot;

    public StorageService(@Value("${outputhub.storage-root:storage}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public Path roundFolder(String roundName) {
        Path folder = storageRoot.resolve(sanitize(roundName) + "-" + LocalDate.now().format(ROUND_FOLDER_FORMAT));
        return folder;
    }

    /** REQ-outputhub-003: 원본을 storage/<차수>/ 에 저장하고 SHA-256을 계산한다. */
    public StoredFile store(String roundName, MultipartFile file) {
        Path folder = roundFolder(roundName);
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(uniqueStoredName(file.getOriginalFilename()));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(target, sha256(target));
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다. 다시 시도하세요", e);
        }
    }

    /** REQ-outputhub-007: 이전 버전 원본을 old 폴더로 수집한다.
     * 트랜잭션 롤백 뒤 재시도로 이미 수집된 파일은 다시 옮기지 않고 old 경로를 그대로 돌려준다. */
    public Path collectOld(String roundName, Path currentPath) {
        Path oldFolder = roundFolder(roundName).resolve(OLD_FOLDER);
        Path target = oldFolder.resolve(currentPath.getFileName().toString());
        if (!Files.exists(currentPath)) {
            if (Files.exists(target)) {
                return target;
            }
            throw new IllegalStateException("수집할 이전 버전 원본을 찾을 수 없습니다: "
                    + currentPath.getFileName());
        }
        try {
            Files.createDirectories(oldFolder);
            Files.move(currentPath, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("이전 버전 수집에 실패했습니다", e);
        }
    }

    /** 등록·확정 전에 원본이 저장 위치에 없으면 old 폴더의 같은 이름 파일을 원래 자리로 되돌려 온다.
     * 트랜잭션 롤백이 파일 이동까지 되돌리지 못해 old에 고립된 최신본을 복구하는 데 쓴다. 없으면 null. */
    public Path recoverFromOld(String roundName, Path currentPath) {
        Path candidate = roundFolder(roundName).resolve(OLD_FOLDER)
                .resolve(currentPath.getFileName().toString());
        if (!Files.exists(candidate)) {
            return null;
        }
        try {
            Files.createDirectories(currentPath.getParent());
            Files.move(candidate, currentPath);
            return currentPath;
        } catch (IOException e) {
            throw new IllegalStateException("old 폴더의 원본 복구에 실패했습니다", e);
        }
    }

    /** REQ-outputhub-022: 원본 파일을 같은 폴더 안에서 규칙 파일명으로 바꾼다. 다른 파일이 이미 그 이름을 쓰고 있으면 실패시킨다. */
    public Path rename(Path currentPath, String newFileName) {
        Path current = currentPath.toAbsolutePath().normalize();
        Path target = current.getParent().resolve(sanitize(newFileName));
        if (Files.exists(target) && !target.equals(current)) {
            throw new IllegalStateException("같은 이름의 산출물 파일이 이미 있습니다: " + target.getFileName());
        }
        try {
            Files.move(current, target);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("파일명 변경에 실패했습니다", e);
        }
    }

    public Path root() {
        return storageRoot;
    }

    public Path resolve(String storedPath) {
        return Path.of(storedPath);
    }

    private String sanitize(String name) {
        String cleaned = name == null ? "unnamed" : name;
        return cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 같은 원본 파일명을 다시 올려도 이전 접수의 저장 파일과 경로가 겹치지 않게 고유한 저장명을 만든다.
     * 화면 표시 파일명은 Submission.fileName을 쓰므로 저장명은 규칙 파일명으로 바뀌기 전의 임시 이름이다. */
    private String uniqueStoredName(String originalFileName) {
        String sanitized = sanitize(originalFileName);
        int dot = sanitized.lastIndexOf('.');
        String stem = dot < 0 ? sanitized : sanitized.substring(0, dot);
        String extension = dot < 0 ? "" : sanitized.substring(dot);
        return stem + "_" + UUID.randomUUID().toString().substring(0, UNIQUE_TOKEN_LENGTH) + extension;
    }

    public static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다", e);
        }
    }

    public record StoredFile(Path path, String sha256) {
    }
}

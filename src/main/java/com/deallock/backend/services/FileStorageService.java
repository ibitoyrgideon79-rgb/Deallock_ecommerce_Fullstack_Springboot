package com.deallock.backend.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    public record StoredFile(String key, String contentType, long sizeBytes) {}

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile save(String folder,
                           MultipartFile file,
                           long maxBytes,
                           Set<String> allowedContentTypes) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("empty_file");
        }
        if (file.getSize() > maxBytes) {
            throw new IOException("file_too_large");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!isAllowed(contentType, allowedContentTypes)) {
            throw new IOException("unsupported_content_type");
        }

        String ext = extensionForContentType(contentType);
        String safeFolder = safeFolder(folder);
        String filename = UUID.randomUUID() + ext;

        Path dir = uploadRoot.resolve(safeFolder).normalize();
        if (!dir.startsWith(uploadRoot)) {
            throw new IOException("invalid_upload_path");
        }
        Files.createDirectories(dir);

        Path destination = dir.resolve(filename).normalize();
        if (!destination.startsWith(uploadRoot)) {
            throw new IOException("invalid_upload_path");
        }

        Files.write(destination, file.getBytes());

        String key = safeFolder.isBlank() ? filename : (safeFolder + "/" + filename);
        return new StoredFile(key, contentType, file.getSize());
    }

    public byte[] read(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("missing_key");
        }
        Path file = uploadRoot.resolve(key).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("not_found");
        }
        return Files.readAllBytes(file);
    }

    public Path resolvePath(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("missing_key");
        }
        Path file = uploadRoot.resolve(key).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("not_found");
        }
        return file;
    }

    public String toPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;
        return "/uploads/" + key;
    }

    private boolean isAllowed(String contentType, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        if (allowed.contains(contentType)) return true;
        // Allow patterns like "image/*"
        int slash = contentType.indexOf('/');
        if (slash > 0) {
            String prefix = contentType.substring(0, slash);
            return allowed.contains(prefix + "/*");
        }
        return false;
    }

    private String safeFolder(String raw) {
        String v = raw == null ? "" : raw.trim();
        v = v.replace('\\', '/');
        v = v.replaceAll("/+", "/");
        v = v.replaceAll("^/+", "");
        v = v.replaceAll("/+$", "");
        // Disallow path traversal characters
        if (v.contains("..")) {
            return "";
        }
        return v;
    }

    private String normalizeContentType(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionForContentType(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }
}


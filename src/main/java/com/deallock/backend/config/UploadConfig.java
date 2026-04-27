package com.deallock.backend.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public UploadConfig(@Value("${app.upload-dir:}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalized = uploadDir == null ? "" : uploadDir.trim();
        if (normalized.isBlank()) {
            normalized = Paths.get(System.getProperty("java.io.tmpdir"), "deallock", "uploads").toString();
        }
        Path uploadPath = Paths.get(normalized);
        if (Files.notExists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath);
            } catch (Exception ignored) {
                // If creation fails, we still register the handler; uploads will error at runtime.
            }
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath.toAbsolutePath() + "/");
    }
}

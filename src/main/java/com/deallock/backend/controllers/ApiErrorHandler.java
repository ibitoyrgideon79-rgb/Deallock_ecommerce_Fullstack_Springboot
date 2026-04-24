package com.deallock.backend.controllers;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiErrorHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        if (path != null && path.startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "Upload file is too large. Maximum allowed is 2MB."));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }
}

package com.deallock.backend.controllers;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Upload file is too large. Maximum allowed is 2MB."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> handleMultipart(MultipartException ex, HttpServletRequest request) {
        // Spring sometimes wraps size-limit failures as MultipartException depending on the resolver/container.
        Throwable cause = ex == null ? null : ex.getCause();
        if (cause instanceof MaxUploadSizeExceededException) {
            return handleMaxUpload((MaxUploadSizeExceededException) cause, request);
        }
        String message = ex == null ? "" : String.valueOf(ex.getMessage());
        if (message.toLowerCase().contains("maximum upload size")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "Upload file is too large. Maximum allowed is 2MB."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Invalid upload request. Please re-select the file and try again."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("[{}] Data access error on {} {}: {}", errorId,
                request == null ? "" : request.getMethod(),
                request == null ? "" : request.getRequestURI(),
                ex == null ? "" : ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", "Server error while saving your changes. Please try again.",
                        "errorId", errorId
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled error on {} {}: {}", errorId,
                request == null ? "" : request.getMethod(),
                request == null ? "" : request.getRequestURI(),
                ex == null ? "" : ex.getMessage(),
                ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", "Server error. Please try again.",
                        "errorId", errorId
                ));
    }
}

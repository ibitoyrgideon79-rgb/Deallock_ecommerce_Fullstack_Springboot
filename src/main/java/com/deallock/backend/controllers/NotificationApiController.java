package com.deallock.backend.controllers;

import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.CurrentUserService;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public NotificationApiController(NotificationRepository notificationRepository,
                                     UserRepository userRepository,
                                     CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "limit", required = false) Integer limit,
                                  Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int size = limit == null ? 6 : Math.max(1, Math.min(20, limit));
        var notes = notificationRepository.findByUserOrderByCreatedAtDesc(userOpt.get());
        List<Map<String, Object>> payload = notes.stream().limit(size).map(n -> {
            Map<String, Object> row = new HashMap<>();
            row.put("message", n.getMessage() == null ? "" : n.getMessage());
            row.put("createdAt", n.getCreatedAt());
            row.put("read", n.isRead());
            return row;
        }).toList();
        return ResponseEntity.ok(payload);
    }
}

package com.deallock.backend.controllers;

import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.NotificationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NotificationController(NotificationRepository notificationRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String notifications(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }
        notificationService.markAllRead(userOpt.get());
        return "redirect:/frontend/pages/notifications.html";
    }
}

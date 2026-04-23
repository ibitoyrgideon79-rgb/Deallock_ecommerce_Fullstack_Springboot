package com.deallock.backend.controllers;

import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.NotificationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the logged-in user available to ALL Thymeleaf pages.
 *
 * Why this exists:
 * - A user can be authenticated (session still valid), but if a controller does not
 *   put "currentUser" in the Model, the navbar renders the "Login/Register" state.
 * - That looks like a logout, even though the session is still active.
 *
 * With this advice, pages like "/", "/marketplace", "/terms", "/contactus" will
 * consistently render the correct logged-in header without each controller having
 * to duplicate the same lookup logic.
 */
@ControllerAdvice(annotations = Controller.class)
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CurrentUserModelAdvice(UserRepository userRepository,
                                  NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(@ModelAttribute("currentUser") User currentUser) {
        return currentUser != null && "ROLE_ADMIN".equals(currentUser.getRole());
    }

    @ModelAttribute("notificationCount")
    public long notificationCount(@ModelAttribute("currentUser") User currentUser) {
        return notificationService.countUnread(currentUser);
    }
}

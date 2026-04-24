package com.deallock.backend.controllers;

import com.deallock.backend.entities.User;
import com.deallock.backend.services.CurrentUserService;
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

    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public CurrentUserModelAdvice(CurrentUserService currentUserService,
                                  NotificationService notificationService) {
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Principal principal) {
        return currentUserService.resolve(principal).orElse(null);
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

package com.deallock.backend.services;

import com.deallock.backend.entities.Notification;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.NotificationRepository;
import com.deallock.backend.repositories.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void notifyUser(User user, String message) {
        if (user == null || message == null || message.isBlank()) {
            return;
        }
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        n.setCreatedAt(Instant.now());
        n.setRead(false);
        notificationRepository.save(n);
    }

    public void notifyAdmins(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        List<User> admins = userRepository.findByRole("ROLE_ADMIN");
        admins.forEach(admin -> notifyUser(admin, message));
    }

    public long countUnread(User user) {
        if (user == null) {
            return 0;
        }
        return notificationRepository.countByUserAndReadIsFalse(user);
    }

    @Transactional
    public void markAllRead(User user) {
        if (user == null) {
            return;
        }
        notificationRepository.markAllReadByUser(user);
    }
}

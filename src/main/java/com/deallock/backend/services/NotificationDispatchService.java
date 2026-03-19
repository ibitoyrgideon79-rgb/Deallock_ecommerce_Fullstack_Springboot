package com.deallock.backend.services;

import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final UserRepository userRepository;

    public NotificationDispatchService(NotificationService notificationService,
                                       EmailService emailService,
                                       SmsService smsService,
                                       UserRepository userRepository) {
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.userRepository = userRepository;
    }

    public void notifyUser(User user,
                           String inAppMessage,
                           String emailSubject,
                           String emailBody,
                           String smsBody) {
        if (user == null) {
            return;
        }
        if (inAppMessage != null && !inAppMessage.isBlank()) {
            notificationService.notifyUser(user, inAppMessage);
        }
        if (user.getEmail() != null && emailSubject != null && emailBody != null) {
            emailService.sendGeneric(user.getEmail(), emailSubject, emailBody);
        }
        if (user.getPhone() != null && smsBody != null && !smsBody.isBlank()) {
            smsService.sendToUser(user.getPhone(), smsBody);
            smsService.sendWhatsAppToUser(user.getPhone(), smsBody);
        }
    }

    public void notifyAdmins(String inAppMessage,
                             String emailSubject,
                             String emailBody,
                             String smsBody) {
        if (inAppMessage != null && !inAppMessage.isBlank()) {
            notificationService.notifyAdmins(inAppMessage);
        }
        List<User> admins = userRepository.findByRole("ROLE_ADMIN");
        if (emailSubject != null && emailBody != null) {
            admins.stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .forEach(e -> emailService.sendGeneric(e, emailSubject, emailBody));
        }
        if (smsBody != null && !smsBody.isBlank()) {
            smsService.sendToAdmins(smsBody);
            smsService.sendWhatsAppToAdmins(smsBody);
        }
    }
}

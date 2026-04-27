package com.deallock.backend.services;

import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

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
            try {
                notificationService.notifyUser(user, inAppMessage);
            } catch (RuntimeException ex) {
                log.warn("In-app user notification failed for userId={}. Continuing.", user.getId(), ex);
            }
        }
        if (user.getEmail() != null && emailSubject != null && emailBody != null) {
            try {
                emailService.sendGeneric(user.getEmail(), emailSubject, emailBody);
            } catch (RuntimeException ex) {
                log.warn("Email notification failed for userId={}. Continuing.", user.getId(), ex);
            }
        }
        if (user.getPhone() != null && smsBody != null && !smsBody.isBlank()) {
            try {
                smsService.sendToUser(user.getPhone(), smsBody);
            } catch (RuntimeException ex) {
                log.warn("SMS notification failed for userId={}. Continuing.", user.getId(), ex);
            }
            try {
                smsService.sendWhatsAppToUser(user.getPhone(), smsBody);
            } catch (RuntimeException ex) {
                log.warn("WhatsApp notification failed for userId={}. Continuing.", user.getId(), ex);
            }
        }
    }

    public void notifyAdmins(String inAppMessage,
                             String emailSubject,
                             String emailBody,
                             String smsBody) {
        if (inAppMessage != null && !inAppMessage.isBlank()) {
            try {
                notificationService.notifyAdmins(inAppMessage);
            } catch (RuntimeException ex) {
                log.warn("In-app admin notification failed. Continuing.", ex);
            }
        }
        List<User> admins = userRepository.findByRole("ROLE_ADMIN");
        if (emailSubject != null && emailBody != null) {
            admins.stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .forEach(e -> {
                        try {
                            emailService.sendGeneric(e, emailSubject, emailBody);
                        } catch (RuntimeException ex) {
                            log.warn("Admin email notification failed for {}. Continuing.", e, ex);
                        }
                    });
        }
        if (smsBody != null && !smsBody.isBlank()) {
            try {
                smsService.sendToAdmins(smsBody);
            } catch (RuntimeException ex) {
                log.warn("Admin SMS notification failed. Continuing.", ex);
            }
            try {
                smsService.sendWhatsAppToAdmins(smsBody);
            } catch (RuntimeException ex) {
                log.warn("Admin WhatsApp notification failed. Continuing.", ex);
            }
        }
    }
}

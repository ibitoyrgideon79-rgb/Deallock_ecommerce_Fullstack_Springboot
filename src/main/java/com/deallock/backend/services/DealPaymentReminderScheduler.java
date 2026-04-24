package com.deallock.backend.services;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealPaymentReminderScheduler {

    private final DealRepository dealRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SmsService smsService;

    public DealPaymentReminderScheduler(DealRepository dealRepository,
                                        NotificationService notificationService,
                                        EmailService emailService,
                                        SmsService smsService) {
        this.dealRepository = dealRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @Transactional
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L)
    public void sendPaymentRemindersEveryTwoDays() {
        List<Deal> deals = dealRepository.findByStatusIgnoreCaseAndPaymentStatusIgnoreCase("Approved", "NOT_PAID");
        if (deals.isEmpty()) return;

        Instant now = Instant.now();
        for (Deal deal : deals) {
            if (deal == null || deal.getUser() == null) continue;
            if (deal.getLastPaymentReminderAt() != null
                    && deal.getLastPaymentReminderAt().isAfter(now.minus(Duration.ofDays(2)))) {
                continue;
            }

            String title = safe(deal.getTitle(), "your deal");
            String due = deal.getPaymentDueAt() == null
                    ? "as soon as possible"
                    : DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                    .withZone(ZoneId.of("Africa/Lagos"))
                    .format(deal.getPaymentDueAt());
            String msg = "Reminder: complete payment for " + title + ". Due: " + due + ".";

            notificationService.notifyUser(deal.getUser(), msg);

            boolean emailed = false;
            if (deal.getUser().getEmail() != null && !deal.getUser().getEmail().isBlank()) {
                emailed = emailService.sendGenericWithStatus(
                        deal.getUser().getEmail(),
                        "Deal payment reminder",
                        msg + "\n\nPlease pay on time to avoid delays."
                );
            }

            // Fallback path: if email unavailable/failed, use SMS.
            if (!emailed && deal.getUser().getPhone() != null && !deal.getUser().getPhone().isBlank()) {
                smsService.sendToUser(deal.getUser().getPhone(), msg);
                smsService.sendWhatsAppToUser(deal.getUser().getPhone(), msg);
            }

            deal.setLastPaymentReminderAt(now);
            dealRepository.save(deal);
        }
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value;
    }
}

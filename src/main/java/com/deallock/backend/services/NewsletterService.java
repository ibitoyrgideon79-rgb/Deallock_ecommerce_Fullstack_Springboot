package com.deallock.backend.services;

import com.deallock.backend.entities.NewsletterSubscription;
import com.deallock.backend.repositories.NewsletterSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NewsletterService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final NewsletterSubscriptionRepository repository;
    private final EmailService emailService;

    public NewsletterService(NewsletterSubscriptionRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    public SubscribeResult subscribe(String emailRaw, String fullNameRaw, String sourceRaw) {
        String email = normalizeEmail(emailRaw);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return new SubscribeResult(false, false, "Valid email is required");
        }

        String fullName = normalizeText(fullNameRaw, 255);
        String source = normalizeText(sourceRaw, 100);
        if (source == null || source.isBlank()) source = "website";

        var existingOpt = repository.findByEmail(email);
        NewsletterSubscription sub;
        boolean created = existingOpt.isEmpty();
        if (created) {
            sub = new NewsletterSubscription();
            sub.setEmail(email);
            sub.setActive(true);
        } else {
            sub = existingOpt.get();
            sub.setActive(true);
        }

        if (fullName != null && !fullName.isBlank()) sub.setFullName(fullName);
        sub.setSource(source);
        repository.save(sub);

        if (created) {
            String body = "Hi,\n\n"
                    + "Thanks for subscribing to DealLock updates.\n"
                    + "You will receive short updates about our services, deals, and payment tips.\n\n"
                    + "If this wasn't you, contact info@deallock.ng.\n\n"
                    + "Team DealLock";
            emailService.sendGeneric(email, "Welcome to DealLock Updates", body);
        }

        return new SubscribeResult(true, created, created ? "Subscribed successfully" : "You are already subscribed");
    }

    @Scheduled(cron = "${app.newsletter.daily-cron:0 0 9 * * *}", zone = "${app.newsletter.timezone:Africa/Lagos}")
    public void sendDailyNewsletterDigest() {
        Instant cutoff = Instant.now().minus(23, ChronoUnit.HOURS);
        Map<Long, NewsletterSubscription> dedup = new LinkedHashMap<>();
        repository.findByActiveTrueAndLastDigestSentAtIsNull().forEach(s -> dedup.put(s.getId(), s));
        repository.findByActiveTrueAndLastDigestSentAtBefore(cutoff).forEach(s -> dedup.put(s.getId(), s));
        List<NewsletterSubscription> due = dedup.values().stream().toList();
        if (due.isEmpty()) return;

        String subject = "DealLock Daily Update";
        String body = buildDailyDigestBody();
        Instant now = Instant.now();
        for (NewsletterSubscription sub : due) {
            if (sub.getEmail() == null || sub.getEmail().isBlank()) continue;
            emailService.sendGeneric(sub.getEmail(), subject, body);
            sub.setLastDigestSentAt(now);
            repository.save(sub);
        }
    }

    private String buildDailyDigestBody() {
        return "Hello from DealLock,\n\n"
                + "We help buyers lock fast-moving deals and complete payment in flexible steps.\n\n"
                + "Why keep DealLock in mind:\n"
                + "- Secure deal support from listing to delivery\n"
                + "- Flexible payment options for high-value purchases\n"
                + "- Transparent process with status updates\n\n"
                + "Need help now?\n"
                + "Email: info@deallock.ng\n"
                + "Phone: +234 703 103 1944\n\n"
                + "DealLock\n"
                + "Pay small. Lock the deal. Secure the item.";
    }

    private String normalizeEmail(String emailRaw) {
        if (emailRaw == null) return null;
        return emailRaw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() > max) return v.substring(0, max);
        return v;
    }

    public record SubscribeResult(boolean ok, boolean created, String message) {
    }
}

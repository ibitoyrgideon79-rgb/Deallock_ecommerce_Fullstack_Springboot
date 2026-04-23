package com.deallock.backend.controllers;

import com.deallock.backend.entities.ContactMessage;
import com.deallock.backend.repositories.ContactMessageRepository;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.NewsletterService;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PublicEngagementApiController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final ContactMessageRepository contactMessageRepository;
    private final EmailService emailService;
    private final NewsletterService newsletterService;

    @Value("${app.contact.inbox:info@deallock.ng}")
    private String contactInbox;

    public PublicEngagementApiController(ContactMessageRepository contactMessageRepository,
                                         EmailService emailService,
                                         NewsletterService newsletterService) {
        this.contactMessageRepository = contactMessageRepository;
        this.emailService = emailService;
        this.newsletterService = newsletterService;
    }

    @PostMapping("/contact")
    public ResponseEntity<?> submitContact(@RequestBody ContactRequest req) {
        String name = normalize(req.name(), 150);
        String email = normalize(req.email(), 255);
        String message = normalize(req.message(), 4000);
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid email is required"));
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message is required"));
        }

        ContactMessage row = new ContactMessage();
        row.setName(name);
        row.setEmail(email);
        row.setMessage(message);
        row.setCreatedAt(Instant.now());
        contactMessageRepository.save(row);

        String subject = "New Contact Message - DealLock";
        String body = "Name: " + name + "\n"
                + "Email: " + email + "\n\n"
                + "Message:\n" + message + "\n";
        emailService.sendGeneric(contactInbox, subject, body);

        String ackBody = "Hi " + name + ",\n\n"
                + "Thanks for contacting DealLock. We received your message and we will get back shortly.\n\n"
                + "Team DealLock";
        emailService.sendGeneric(email, "We received your message", ackBody);

        return ResponseEntity.ok(Map.of("message", "Message sent successfully. We'll keep in touch shortly."));
    }

    @PostMapping("/newsletter/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody NewsletterRequest req) {
        var result = newsletterService.subscribe(req.email(), req.name(), req.source());
        if (!result.ok()) {
            return ResponseEntity.badRequest().body(Map.of("message", result.message()));
        }
        return ResponseEntity.ok(Map.of(
                "message", result.message(),
                "created", result.created()
        ));
    }

    private String normalize(String v, int maxLen) {
        if (v == null) return null;
        String t = v.trim();
        if (t.length() > maxLen) return t.substring(0, maxLen);
        return t;
    }

    public record ContactRequest(String name, String email, String message) {
    }

    public record NewsletterRequest(String email, String name, String source) {
    }
}

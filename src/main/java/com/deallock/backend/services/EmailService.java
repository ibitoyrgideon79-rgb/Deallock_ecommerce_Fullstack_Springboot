package com.deallock.backend.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;

     @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${RESEND_FROM:no-reply@send.deallock.ng}")
    private String resendFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
         this.httpClient = HttpClient.newHttpClient();
    }

    private void send(String to, String subject, String text) {
        if (!isBlank(resendApiKey)) {
            try {
                sendViaResend(to, subject, text);
                return;
            } catch (Exception ex) {
                System.out.println("[WARN] Resend send failed, falling back to SMTP: " + ex.getMessage());
            }
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            System.out.println("[WARN] SMTP send failed: " + ex.getMessage());
        }
    }

    public void sendGeneric(String email, String subject, String body) {
        if (isBlank(email) || isBlank(subject) || isBlank(body)) {
            return;
        }
        send(email, subject, body);
    }

    private void sendViaResend(String to, String subject, String text) throws Exception {
        String from = isBlank(resendFrom) ? "no-reply@send.deallock.ng" : resendFrom;
        String payload = "{\"from\":\"" + escapeJson(from) + "\","
                + "\"to\":[\"" + escapeJson(to) + "\"],"
                + "\"subject\":\"" + escapeJson(subject) + "\","
                + "\"text\":\"" + escapeJson(text) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Resend HTTP " + code + ": " + response.body());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public void sendOtp(String email, String otp) {
        send(email, "Your OTP Code", "Your OTP is: " + otp);
    }

    public void sendActivationLink(String email, String link) {
        send(email, "Activate your account", "Click: " + link);
    }

    public void sendPasswordResetLink(String email, String link) {
        send(email, "Reset your password", "Click: " + link);
    }

    public void sendDealCreatedToAdmin(String email, String details) {
        send(email, "New Deal Created", details);
    }

    public void sendDealCreatedToUser(String email, String details) {
        send(email, "Your Deal Was Created", details);
    }

    public void sendDealApprovedToUser(String email, String details) {
        send(email, "Your Deal Was Approved", details);
    }

    public void sendDealApprovedToAdmin(String email, String details) {
        send(email, "Deal Approved", details);
    }

    public void sendDealRejectedToUser(String email, String details) {
        send(email, "Deal Rejected", details);
    }

    public void sendPaymentConfirmedToUser(String email, String details) {
        send(email, "Payment Confirmed", details);
    }

    public void sendPaymentNotReceivedToUser(String email, String details) {
        send(email, "Payment Not Received", details);
    }

    public void sendDealSecuredToUser(String email, String details) {
        send(email, "Deal Secured", details);
    }

    public void sendPaymentProofReceivedToUser(String email, String details) {
        send(email, "Payment Proof Received", details);
    }

    public void sendPaymentProofReceivedToAdmin(String email, String details) {
        send(email, "Payment Proof Uploaded", details);
    }
}

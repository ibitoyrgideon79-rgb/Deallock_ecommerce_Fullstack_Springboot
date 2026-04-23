package com.deallock.backend.services;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    @Value("${SMTP_FROM:no-reply@deallock.ng}")
    private String smtpFromEmail;

    @Value("${SMTP_FROM_NAME:DealLock}")
    private String smtpFromName;

    public EmailService(@Nullable JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private void send(String to, String subject, String text) {
        if (mailSender == null) {
            System.out.println("[WARN] JavaMailSender bean not configured; skipping email send.");
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            helper.setFrom(new InternetAddress(smtpFromEmail, smtpFromName).toString());
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public void sendOtp(String email, String otp) {
        send(email, "DealLock OTP", "Your OTP is: " + otp);
    }

    public void sendActivationLink(String email, String link) {
        send(email, "DealLock Activation", "Click: " + link);
    }

    public void sendLoginAlert(String email, String details) {
        send(email, "New login to your DealLock account", details);
    }

    public void sendWelcomeEmail(String email, String details) {
        send(email, "Welcome to DealLock", details);
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

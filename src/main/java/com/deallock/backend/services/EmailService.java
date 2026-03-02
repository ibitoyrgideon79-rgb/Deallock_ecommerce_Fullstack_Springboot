package com.deallock.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private void send(String to, String subject, String text) {
        if (fromAddress == null || fromAddress.isBlank()) {
            System.out.println("[DEV] SMTP not configured. To: " + to + " | " + subject + " | " + text);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(fromAddress);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            System.out.println("[DEV] SMTP send failed: " + ex.getMessage());
        }
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
}

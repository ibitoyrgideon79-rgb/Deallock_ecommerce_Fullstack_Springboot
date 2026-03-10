package com.deallock.backend.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private void send(String to, String subject, String text) {
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

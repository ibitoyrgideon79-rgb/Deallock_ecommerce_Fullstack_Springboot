package com.deallock.backend.services;

import org.springframework.stereotype.Service;

@Service
public class EmailService {
    public void sendOtp(String email, String otp) {
        System.out.println("OTP for " + email + " is: " + otp);
    }

    public void sendActivationLink(String email, String link) {
        System.out.println("Activation link for " + email + ": " + link);
    }

    public void sendPasswordResetLink(String email, String link) {
        System.out.println("Password reset link for " + email + ": " + link);
    }
}

package com.deallock.backend.controllers;

import com.deallock.backend.entities.PasswordResetToken;
import com.deallock.backend.repositories.PasswordResetTokenRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import java.time.Instant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordController {

    private final PasswordResetTokenRepository resetRepo;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public PasswordController(PasswordResetTokenRepository resetRepo, UserRepository userRepository, EmailService emailService) {
        this.resetRepo = resetRepo;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam("email") String email, Model model) {
        if (userRepository.findByEmail(email).isEmpty()) {
            model.addAttribute("message", "If that email exists, we sent a reset link.");
            return "forgot-password";
        }

        String token = java.util.UUID.randomUUID().toString();
        PasswordResetToken entry = new PasswordResetToken();
        entry.setEmail(email);
        entry.setToken(token);
        entry.setExpiresAt(Instant.now().plusSeconds(3600));
        entry.setUsed(false);
        resetRepo.save(entry);

        String link = "http://localhost:8080/reset-password?token=" + token;
        emailService.sendPasswordResetLink(email, link);

        model.addAttribute("message", "Check your email for the reset link.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token, Model model) {
        var tokenOpt = resetRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid reset link.");
            return "reset-password";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            model.addAttribute("error", "Reset link expired.");
            return "reset-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam("token") String token,
                                      @RequestParam("password") String password,
                                      Model model) {
        var tokenOpt = resetRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid reset link.");
            return "reset-password";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            model.addAttribute("error", "Reset link expired.");
            return "reset-password";
        }

        var userOpt = userRepository.findByEmail(entry.getEmail());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found.");
            return "reset-password";
        }

        var user = userOpt.get();
        user.setPassword(new BCryptPasswordEncoder().encode(password));
        userRepository.save(user);

        entry.setUsed(true);
        resetRepo.save(entry);

        return "redirect:/login?reset=true";
    }
}

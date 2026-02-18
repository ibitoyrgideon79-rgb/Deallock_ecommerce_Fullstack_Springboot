package com.deallock.backend.controllers;

import com.deallock.backend.dtos.OtpRequest;
import com.deallock.backend.dtos.OtpverifyRequest;
import com.deallock.backend.dtos.SignupRequest;
import com.deallock.backend.entities.ActivationToken;
import com.deallock.backend.entities.OtpCode;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.OtpCodeRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpRepo;
    private final ActivationTokenRepository activationRepo;
    private final EmailService emailService;

    public AuthApiController(UserRepository userRepository, OtpCodeRepository otpRepo, ActivationTokenRepository activationRepo, EmailService emailService) {
        this.userRepository = userRepository;
        this.otpRepo = otpRepo;
        this.activationRepo = activationRepo;
        this.emailService = emailService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest req) {
        String otp = String.format("%06d", new Random().nextInt(999999));

        OtpCode entry = new OtpCode();
        entry.setEmail(req.email);
        entry.setCode(otp);
        entry.setExpiresAt(Instant.now().plusSeconds(300));
        entry.setVerified(false);

        otpRepo.save(entry);

        emailService.sendOtp(req.email, otp);
        return ResponseEntity.ok(Map.of("message", "OTP sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpverifyRequest req) {
        var entryOpt = otpRepo.findTopByEmailOrderByIdDesc(req.email);

        if (entryOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No OTP found"));
        }

        var entry = entryOpt.get();

        if (entry.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
        }

        if (!entry.getCode().equals(req.otp)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
        }

        entry.setVerified(true);
        otpRepo.save(entry);

        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest req) {
        if (userRepository.findByEmail(req.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }

        var entryOpt = otpRepo.findTopByEmailOrderByIdDesc(req.email);

        if (entryOpt.isEmpty() || !entryOpt.get().isVerified()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email not verified"));
        }

        User user = User.builder()
                .fullName(req.fullName)
                .email(req.email)
                .username(req.username)
                .password(new BCryptPasswordEncoder().encode(req.password))
                .address(req.address)
                .dateOfBirth(LocalDate.parse(req.dob))
                .role("Role.USER.name()")
                .enabled(false)
                .creation(new java.util.Date())
                .build();

        userRepository.save(user);

        String token = java.util.UUID.randomUUID().toString();
        ActivationToken activation = new ActivationToken();
        activation.setEmail(req.email);
        activation.setToken(token);
        activation.setExpiresAt(Instant.now().plusSeconds(3600));
        activation.setUsed(false);
        activationRepo.save(activation);

        String link = "http://localhost:8080/activate?token=" + token;
        emailService.sendActivationLink(req.email, link);

        return ResponseEntity.ok(Map.of("message", "Account created"));
    }
}

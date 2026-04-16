package com.deallock.backend.controllers;

import com.deallock.backend.dtos.OtpRequest;
import com.deallock.backend.dtos.OtpverifyRequest;
import com.deallock.backend.dtos.RegisterDto;
import com.deallock.backend.entities.ActivationToken;
import com.deallock.backend.entities.OtpCode;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.OtpCodeRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.AuditLogService;
import com.deallock.backend.services.EmailService;
import com.deallock.backend.services.SmsService;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PHONE_REGEX = "^\\+[1-9]\\d{7,14}$";

    private final UserRepository userRepository;
    private final OtpCodeRepository otpRepo;
    private final ActivationTokenRepository activationRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SmsService smsService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public AuthApiController(UserRepository userRepository,
                             OtpCodeRepository otpRepo,
                             ActivationTokenRepository activationRepo,
                             EmailService emailService,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService,
                             SmsService smsService) {
        this.userRepository = userRepository;
        this.otpRepo = otpRepo;
        this.activationRepo = activationRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.smsService = smsService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest req, HttpServletRequest request) {
        String channel = req.channel == null || req.channel.isBlank() ? "email" : req.channel.toLowerCase();
        String email = req.email == null ? null : req.email.trim();
        String phone = req.phone == null ? null : req.phone.trim();

        if ("phone".equals(channel)) {
            if (phone == null || phone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone is required"));
            }
            if (!phone.matches(PHONE_REGEX)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number must be in international format e.g. +2348012345678"));
            }
        } else {
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        String otpMessage = "Your DealLock OTP is: " + otp;
        if ("phone".equals(channel)) {
            SmsService.SmsResult smsResult = smsService.sendSmsResult(phone, otpMessage);
            if (!smsResult.ok) {
                auditLogService.log("OTP_SENT", phone, request, false, smsResult.message);
                return ResponseEntity.badRequest().body(Map.of("message", smsResult.message));
            }
        } else {
            try {
                emailService.sendOtp(email, otp);
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "Email could not be sent";
                auditLogService.log("OTP_SENT", email, request, false, msg);
                return ResponseEntity.badRequest().body(Map.of("message", "Email could not be sent. Please try again."));
            }
        }

        OtpCode entry = new OtpCode();
        entry.setEmail(email);
        entry.setPhone(phone);
        entry.setChannel(channel);
        entry.setCode(otp);
        entry.setExpiresAt(Instant.now().plusSeconds(300));
        entry.setVerified(false);

        otpRepo.save(entry);

        auditLogService.log("OTP_SENT", "phone".equals(channel) ? phone : email, request, true, null);
        return ResponseEntity.ok(Map.of("message", "OTP sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpverifyRequest req, HttpServletRequest request) {
        String channel = req.channel == null || req.channel.isBlank() ? "email" : req.channel.toLowerCase();
        if ("phone".equals(channel)) {
            if (req.phone == null || req.phone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone is required"));
            }
            if (!req.phone.trim().matches(PHONE_REGEX)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number must be in international format e.g. +2348012345678"));
            }
        } else {
            if (req.email == null || req.email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
        }
        var entryOpt = "phone".equals(channel)
                ? otpRepo.findTopByPhoneOrderByIdDesc(req.phone)
                : otpRepo.findTopByEmailOrderByIdDesc(req.email);

        if (entryOpt.isEmpty()) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "no_otp");
            return ResponseEntity.badRequest().body(Map.of("message", "No OTP found"));
        }

        var entry = entryOpt.get();

        if (entry.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "expired");
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
        }

        if (!entry.getCode().equals(req.otp)) {
            auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, false, "invalid");
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
        }

        entry.setVerified(true);
        otpRepo.save(entry);

        auditLogService.log("OTP_VERIFY", "phone".equals(channel) ? req.phone : req.email, request, true, null);
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> signup(@Validated @RequestBody RegisterDto req, HttpServletRequest request) {
        String email = req.getEmail() == null ? null : req.getEmail().trim();
        String phone = req.getPhone() == null ? null : req.getPhone().trim();

        if (phone != null && !phone.isBlank() && !phone.matches(PHONE_REGEX)) {
            auditLogService.log("SIGNUP", email, request, false, "phone_invalid");
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number must be in international format e.g. +2348012345678"));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            auditLogService.log("SIGNUP", email, request, false, "email_exists");
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }

        var emailEntry = otpRepo.findTopByEmailOrderByIdDesc(email);
        var phoneEntry = phone == null || phone.isBlank()
                ? java.util.Optional.<OtpCode>empty()
                : otpRepo.findTopByPhoneOrderByIdDesc(phone);
        boolean verified = (emailEntry.isPresent() && emailEntry.get().isVerified())
                || (phoneEntry.isPresent() && phoneEntry.get().isVerified());

        if (!verified) {
            auditLogService.log("SIGNUP", email, request, false, "contact_not_verified");
            return ResponseEntity.badRequest().body(Map.of("message", "Verify your email or phone OTP before signing up"));
        }

        if (req.getPassword() == null || !req.getPassword().equals(req.getConfirmPassword())) {
            auditLogService.log("SIGNUP", email, request, false, "password_mismatch");
            return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
        }

        User user = User.builder()
                .fullName(req.getFullName())
                .email(email)
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .address(req.getAddress())
                .phone(phone)
                .dateOfBirth(req.getDateOfBirth())
                .role("ROLE_USER")
                .enabled(false)
                .creation(Instant.now())
                .build();

        userRepository.save(user);

        String token = java.util.UUID.randomUUID().toString();
        ActivationToken activation = new ActivationToken();
        activation.setEmail(email);
        activation.setToken(token);
        activation.setExpiresAt(Instant.now().plusSeconds(3600));
        activation.setUsed(false);
        activationRepo.save(activation);

        String link = baseUrl + "/activate?token=" + token;
        emailService.sendActivationLink(email, link);
        // Consume the OTP that was used for verification now that signup is successful
        emailEntry.ifPresent(otpRepo::delete);
        phoneEntry.ifPresent(otpRepo::delete);

        auditLogService.log("SIGNUP", email, request, true, null);
        return ResponseEntity.ok(Map.of(
                "message", "Account created",
                "activationLink", link
        ));
    }
}

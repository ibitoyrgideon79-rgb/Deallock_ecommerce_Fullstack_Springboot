package com.deallock.backend.controllers;

import com.deallock.backend.dtos.OtpRequest;
import com.deallock.backend.dtos.OtpverifyRequest;
import com.deallock.backend.dtos.SignupInitRequest;
import com.deallock.backend.dtos.CompleteProfileRequest;
import com.deallock.backend.dtos.OtpLoginRequest;
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
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    private static final String ADMIN_EMAIL = "info@deallock.ng";

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
    public ResponseEntity<?> signupInit(@Validated @RequestBody SignupInitRequest req, HttpServletRequest request) {
        String channel = req.channel == null || req.channel.isBlank() ? "email" : req.channel.toLowerCase();
        String email = req.email == null ? null : req.email.trim();
        String phone = req.phone == null ? null : req.phone.trim();

        if ("phone".equals(channel)) {
            if (phone == null || phone.isBlank()) {
                auditLogService.log("SIGNUP_INIT", null, request, false, "phone_missing");
                return ResponseEntity.badRequest().body(Map.of("message", "Phone is required"));
            }
            if (!phone.matches(PHONE_REGEX)) {
                auditLogService.log("SIGNUP_INIT", null, request, false, "phone_invalid");
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number must be in international format e.g. +2348012345678"));
            }
        } else {
            if (email == null || email.isBlank()) {
                auditLogService.log("SIGNUP_INIT", null, request, false, "email_missing");
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
        }

        if (email != null && userRepository.findByEmail(email).isPresent()) {
            auditLogService.log("SIGNUP_INIT", email, request, false, "email_exists");
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }
        if (phone != null && !phone.isBlank() && userRepository.findByPhone(phone).isPresent()) {
            auditLogService.log("SIGNUP_INIT", email != null ? email : phone, request, false, "phone_exists");
            return ResponseEntity.badRequest().body(Map.of("message", "Phone already exists"));
        }

        var emailEntry = email == null ? java.util.Optional.<OtpCode>empty() : otpRepo.findTopByEmailOrderByIdDesc(email);
        var phoneEntry = phone == null || phone.isBlank()
                ? java.util.Optional.<OtpCode>empty()
                : otpRepo.findTopByPhoneOrderByIdDesc(phone);
        boolean verified = (emailEntry.isPresent() && emailEntry.get().isVerified())
                || (phoneEntry.isPresent() && phoneEntry.get().isVerified());

        if (!verified) {
            auditLogService.log("SIGNUP_INIT", email != null ? email : phone, request, false, "contact_not_verified");
            return ResponseEntity.badRequest().body(Map.of("message", "Verify OTP before continuing"));
        }

        boolean isAdminEmail = email != null && ADMIN_EMAIL.equalsIgnoreCase(email);
        User user = User.builder()
                .fullName(null)
                .email(email)
                .username(null)
                .password(null)
                .address(null)
                .phone(phone)
                .dateOfBirth(null)
                .role(isAdminEmail ? "ROLE_ADMIN" : "ROLE_USER")
                // Admin email is trusted via OTP to that mailbox; let admin log in immediately.
                .enabled(isAdminEmail)
                .creation(Instant.now())
                .build();

        userRepository.save(user);

        // Consume the OTP that was used for verification now that signup-init is successful.
        emailEntry.ifPresent(otpRepo::delete);
        phoneEntry.ifPresent(otpRepo::delete);

        auditLogService.log("SIGNUP_INIT", email != null ? email : phone, request, true, null);
        return ResponseEntity.ok(Map.of(
                "message", "Account started. Complete your profile to finish signup."
        ));
    }

    @PostMapping("/profile/complete")
    @Transactional
    public ResponseEntity<?> completeProfile(@Validated @RequestBody CompleteProfileRequest req, HttpServletRequest request) {
        String email = req.email == null ? null : req.email.trim();
        String phone = req.phone == null ? null : req.phone.trim();

        if (phone != null && !phone.isBlank() && !phone.matches(PHONE_REGEX)) {
            auditLogService.log("PROFILE_COMPLETE", email, request, false, "phone_invalid");
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number must be in international format e.g. +2348012345678"));
        }

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() && phone != null && !phone.isBlank()) {
            userOpt = userRepository.findByPhone(phone);
        }
        if (userOpt.isEmpty()) {
            auditLogService.log("PROFILE_COMPLETE", email != null ? email : phone, request, false, "user_not_found");
            return ResponseEntity.badRequest().body(Map.of("message", "Start signup first"));
        }

        var user = userOpt.get();

        // If the user started signup with phone only, allow setting email here.
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            if (userRepository.findByEmail(email).isPresent()) {
                auditLogService.log("PROFILE_COMPLETE", email, request, false, "email_exists");
                return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
            }
            user.setEmail(email);
        } else if (!user.getEmail().equalsIgnoreCase(email)) {
            auditLogService.log("PROFILE_COMPLETE", email, request, false, "email_mismatch");
            return ResponseEntity.badRequest().body(Map.of("message", "Email does not match signup record"));
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            auditLogService.log("PROFILE_COMPLETE", email, request, false, "already_completed");
            return ResponseEntity.badRequest().body(Map.of("message", "Profile already completed"));
        }

        if (!req.password.equals(req.confirmPassword)) {
            auditLogService.log("PROFILE_COMPLETE", email, request, false, "password_mismatch");
            return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
        }

        user.setFullName(req.fullName);
        if (req.username != null && !req.username.isBlank()) {
            user.setUsername(req.username.trim());
        }
        if (req.address != null && !req.address.isBlank()) {
            user.setAddress(req.address.trim());
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone);
        }
        user.setDateOfBirth(req.dateOfBirth);
        user.setPassword(passwordEncoder.encode(req.password));
        if (email != null && ADMIN_EMAIL.equalsIgnoreCase(email)) {
            user.setRole("ROLE_ADMIN");
        }
        user.setEnabled(true);
        userRepository.save(user);

        auditLogService.log("PROFILE_COMPLETE", email, request, true, null);
        return ResponseEntity.ok(Map.of("message", "Profile completed. You can now log in."));
    }

    @PostMapping("/login/otp")
    public ResponseEntity<?> otpLogin(@Validated @RequestBody OtpLoginRequest req, HttpServletRequest request) {
        String login = req.login == null ? null : req.login.trim();
        String otp = req.otp == null ? null : req.otp.trim();

        if (login == null || login.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Login is required"));
        }

        boolean loginLooksLikePhone = login.startsWith("+");
        String channel = req.channel == null || req.channel.isBlank()
                ? (loginLooksLikePhone ? "phone" : "email")
                : req.channel.toLowerCase();

        var entryOpt = "phone".equals(channel)
                ? otpRepo.findTopByPhoneOrderByIdDesc(login)
                : otpRepo.findTopByEmailOrderByIdDesc(login);

        if (entryOpt.isEmpty()) {
            auditLogService.log("LOGIN_OTP", login, request, false, "no_otp");
            return ResponseEntity.badRequest().body(Map.of("message", "No OTP found"));
        }

        var entry = entryOpt.get();
        if (entry.getExpiresAt().isBefore(Instant.now())) {
            auditLogService.log("LOGIN_OTP", login, request, false, "expired");
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
        }
        if (!entry.getCode().equals(otp)) {
            auditLogService.log("LOGIN_OTP", login, request, false, "invalid");
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
        }

        var userOpt = userRepository.findByEmail(login)
                .or(() -> userRepository.findByUsername(login))
                .or(() -> userRepository.findByPhone(login));
        if (userOpt.isEmpty()) {
            auditLogService.log("LOGIN_OTP", login, request, false, "user_not_found");
            return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        }

        var user = userOpt.get();

        boolean isAdminEmail = user.getEmail() != null && ADMIN_EMAIL.equalsIgnoreCase(user.getEmail());
        boolean isAdmin = "ROLE_ADMIN".equals(user.getRole()) || isAdminEmail;

        // Admin bootstrap: if the admin email signed up but never completed profile,
        // allow OTP login and make the account active.
        if (isAdmin) {
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                user.setRole("ROLE_ADMIN");
            }
            if (!user.isEnabled()) {
                user.setEnabled(true);
            }
            userRepository.save(user);
        }

        if (!user.isEnabled()) {
            auditLogService.log("LOGIN_OTP", login, request, false, "disabled");
            return ResponseEntity.status(409).body(Map.of(
                    "message", "Account not active. Complete your profile first.",
                    "nextUrl", "/register"
            ));
        }
        if ((user.getPassword() == null || user.getPassword().isBlank()) && !isAdmin) {
            auditLogService.log("LOGIN_OTP", login, request, false, "profile_incomplete");
            return ResponseEntity.status(409).body(Map.of(
                    "message", "Complete your profile first.",
                    "nextUrl", "/register"
            ));
        }

        String role = isAdmin ? "ROLE_ADMIN" : (user.getRole() == null || user.getRole().isBlank() ? "ROLE_USER" : user.getRole());
        var auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                java.util.List.of(new SimpleGrantedAuthority(role))
        );
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // Consume OTP after successful login
        otpRepo.delete(entry);

        auditLogService.log("LOGIN_OTP", login, request, true, null);
        return ResponseEntity.ok(Map.of(
                "message", "Logged in",
                "nextUrl", isAdmin ? "/admin" : "/dashboard"
        ));
    }
}







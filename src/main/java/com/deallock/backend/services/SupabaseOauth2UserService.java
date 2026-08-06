package com.deallock.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.entities.User;

@Service
public class SupabaseOauth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String FALLBACK_ADMIN_EMAIL = "info@deallock.ng";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserRepository userRepository;

    @Value("${app.admin-emails:}")
    private String configuredAdminEmails;

    public SupabaseOauth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = new DefaultOAuth2UserService().loadUser(userRequest);
        
        // Supabase returns email in the 'email' attribute
        String email = stringAttr(oauthUser, "email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "Supabase account email was not provided."
            );
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        final String normalizedEmail = email;

        String fullName = stringAttr(oauthUser, "name");
        boolean admin = isAdminEmail(normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            User created = new User();
            created.setEmail(normalizedEmail);
            created.setUsername(generateUniqueUsername(normalizedEmail));
            created.setPassword(PASSWORD_ENCODER.encode(UUID.randomUUID().toString()));
            created.setRole(admin ? "ROLE_ADMIN" : "ROLE_USER");
            created.setEnabled(true);
            created.setCreation(Instant.now());
            return created;
        });

        if (user.getCreation() == null) {
            user.setCreation(Instant.now());
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(PASSWORD_ENCODER.encode(UUID.randomUUID().toString()));
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(generateUniqueUsername(normalizedEmail));
        }
        if (fullName != null && !fullName.isBlank() && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(fullName.trim());
        }
        user.setEnabled(true);
        user.setRole(admin ? "ROLE_ADMIN" : normalizeRole(user.getRole()));
        userRepository.save(user);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(user.getRole()));
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("email", normalizedEmail);
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            attributes.put("name", user.getFullName());
        }
        return new DefaultOAuth2User(authorities, attributes, "email");
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank() || !role.startsWith("ROLE_")) {
            return "ROLE_USER";
        }
        return role;
    }

    private boolean isAdminEmail(String email) {
        if (FALLBACK_ADMIN_EMAIL.equalsIgnoreCase(email)) {
            return true;
        }
        if (configuredAdminEmails == null || configuredAdminEmails.isBlank()) {
            return false;
        }
        for (String adminEmail : configuredAdminEmails.split(",")) {
            if (adminEmail.trim().equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_-]", "_");
        String username = base;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + "_" + counter;
            counter++;
        }
        return username;
    }

    private String stringAttr(OAuth2User user, String name) {
        Object attr = user.getAttribute(name);
        return attr instanceof String ? (String) attr : null;
    }
}

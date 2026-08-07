package com.deallock.backend.services;

import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOauth2UserService extends OidcUserService {

    private static final String FALLBACK_ADMIN_EMAIL = "info@deallock.ng";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserRepository userRepository;

    @Value("${app.admin-emails:}")
    private String configuredAdminEmails;

    public GoogleOauth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        try {
            User user = createOrUpdateUser(oidcUser);
            var authorities = List.of(new SimpleGrantedAuthority(user.getRole()));
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
        } catch (RuntimeException ex) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_registration_failed", "Unable to create or update the local user from Google account data.", null),
                    ex
            );
        }
    }

    private User createOrUpdateUser(OidcUser oidcUser) {
        String email = resolveEmail(oidcUser);
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "Google account email was not provided."
            );
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String fullName = resolveFullName(oidcUser);
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
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(normalizedEmail);
        }
        if (fullName != null && !fullName.isBlank() && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(fullName.trim());
        }
        user.setEnabled(true);
        user.setRole(admin ? "ROLE_ADMIN" : normalizeRole(user.getRole()));
        userRepository.save(user);

        return user;
    }

    private String resolveEmail(OidcUser oidcUser) {
        String email = firstNonBlank(
                stringAttr(oidcUser, "email"),
                stringAttr(oidcUser, "preferred_username"),
                stringAttr(oidcUser, "upn")
        );
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (oidcUser.getIdToken() != null) {
            String idTokenEmail = oidcUser.getIdToken().getEmail();
            if (idTokenEmail != null && !idTokenEmail.isBlank()) {
                return idTokenEmail;
            }
        }
        return null;
    }

    private String resolveFullName(OidcUser oidcUser) {
        String given = stringAttr(oidcUser, "given_name");
        String family = stringAttr(oidcUser, "family_name");
        String name = stringAttr(oidcUser, "name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (given != null || family != null) {
            return (given == null ? "" : given.trim()).trim() + " " + (family == null ? "" : family.trim()).trim();
        }
        if (oidcUser.getIdToken() != null) {
            String idTokenName = oidcUser.getIdToken().getFullName();
            if (idTokenName != null && !idTokenName.isBlank()) {
                return idTokenName.trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank() || !role.startsWith("ROLE_")) {
            return "ROLE_USER";
        }
        return role;
    }

    private boolean isAdminEmail(String email) {
        if (email.equalsIgnoreCase(FALLBACK_ADMIN_EMAIL)) {
            return true;
        }
        if (configuredAdminEmails == null || configuredAdminEmails.isBlank()) {
            return false;
        }
        for (String candidate : configuredAdminEmails.split(",")) {
            if (email.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private String generateUniqueUsername(String email) {
        String localPart = email.split("@")[0].replaceAll("[^A-Za-z0-9_.-]", "");
        if (localPart.isBlank()) {
            localPart = "user";
        }
        String base = localPart.toLowerCase(Locale.ROOT);
        String candidate = base;
        int counter = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            counter++;
            candidate = base + counter;
        }
        return candidate;
    }

    private String stringAttr(OidcUser oidcUser, String key) {
        Object raw = oidcUser.getAttributes().get(key);
        return raw == null ? null : String.valueOf(raw);
    }
}

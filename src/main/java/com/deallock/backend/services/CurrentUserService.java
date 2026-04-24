package com.deallock.backend.services;

import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import java.security.Principal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> resolve(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return Optional.empty();
        }
        return resolveIdentifier(principal.getName());
    }

    public Optional<User> resolve(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if ("anonymousUser".equalsIgnoreCase(name)) {
            return Optional.empty();
        }
        return resolveIdentifier(name);
    }

    public Optional<User> resolveIdentifier(String identifier) {
        if (identifier == null) return Optional.empty();
        String key = identifier.trim();
        if (key.isBlank()) return Optional.empty();
        return userRepository.findByEmail(key)
                .or(() -> userRepository.findByUsername(key))
                .or(() -> userRepository.findByPhone(key));
    }
}


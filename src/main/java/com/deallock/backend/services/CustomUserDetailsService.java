package com.deallock.backend.services;

import com.deallock.backend.repositories.UserRepository;
import java.util.Collections;
import java.time.Instant;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Guard against malformed legacy rows causing 500 during login.
        String username = user.getEmail();
        String password = user.getPassword();
        String role = user.getRole();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new UsernameNotFoundException("Invalid user record");
        }
        if (role == null || role.isBlank() || !role.startsWith("ROLE_")) {
            role = "ROLE_USER";
        }

        boolean accountNonLocked = user.getLockoutUntil() == null
                || user.getLockoutUntil().isBefore(Instant.now());

        return new org.springframework.security.core.userdetails.User(
                username,
                password,
                user.isEnabled(),
                true,
                true,
                accountNonLocked,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}

package com.deallock.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @SuppressWarnings({"java:S112", "java:S1130"})
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/forgot-password",
                        "/reset-password"
                ))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/health",
                                "/login",
                                "/register",
                                "/terms",
                                "/send-otp",
                                "/ourteam",
                                "/contactus",
                                "/marketplace",
                                "/error",
                                "/api/send-otp",
                                "/api/verify-otp",
                                "/api/signup",
                                "/api/profile/complete",
                                "/api/login/otp",
                                "/activate",
                                "/forgot-password",
                                "/reset-password",
                                "/frontend/**",
                                "/pages/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/**").permitAll()
                        .requestMatchers("/api/marketplace/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/profile", "/profile/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin" : "/dashboard");
                        })
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(request ->
                                "GET".equalsIgnoreCase(request.getMethod())
                                        && "/logout".equals(request.getServletPath()))
                        .logoutSuccessUrl("/dashboard")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key("deallock-remember-me")
                        .tokenValiditySeconds(60 * 60 * 24 * 30)
                        .userDetailsService(userDetailsService)
                )
                .exceptionHandling(ex -> ex
                        // APIs should return 401/403 JSON-friendly statuses, not 302 redirects to /login.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> {
                                    String path = request.getServletPath();
                                    return path != null && path.startsWith("/api/");
                                }
                        )
                        .defaultAccessDeniedHandlerFor(
                                (request, response, accessDeniedException) -> response.sendError(HttpStatus.FORBIDDEN.value()),
                                request -> {
                                    String path = request.getServletPath();
                                    return path != null && path.startsWith("/api/");
                                }
                        )
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

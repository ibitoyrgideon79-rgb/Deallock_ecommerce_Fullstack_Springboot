package com.deallock.backend.config;


import org.springframework.boot.web.server.Http2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.method.AuthorizationManagerAfterReactiveMethodInterceptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
            return httpSecurity
                    .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/",
                                    "/login",
                                    "/register",
                                    "/terms",
                                    "/dashboard",
                                    "/ourteam",
                                    "/test",
                                    "/test2",
                                    "/frontend/**",
                                    "/api/send-otp",
                                    "/api/verify-otp",
                                    "/api/signup",
                                    "/activate",
                                    "/forgot-password",
                                    "/reset-password"
                            ).permitAll()
                            .requestMatchers("/profile").hasRole("Role.USER.name()")
                            .anyRequest().authenticated()
                    )
                    .formLogin(form -> form
                            .loginPage("/login")
                            .usernameParameter("email")
                            .passwordParameter("password")
                            .defaultSuccessUrl("/dashboard", true)
                            .failureUrl("/login?error=true")
                            .permitAll()
                    )
                    .logout(logout -> logout
                            .permitAll()
                    )
                    .build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }


    }

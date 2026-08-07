package com.deallock.backend.config;

import com.deallock.backend.services.GoogleOauth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final GoogleOauth2UserService googleOauth2UserService;
    private final Environment env;

    public SecurityConfig(UserDetailsService userDetailsService, GoogleOauth2UserService googleOauth2UserService, Environment env) {
        this.userDetailsService = userDetailsService;
        this.googleOauth2UserService = googleOauth2UserService;
        this.env = env;
    }

    @Bean
    @SuppressWarnings({"java:S112", "java:S1130"})
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        HttpSecurity http = httpSecurity
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
                                "/actuator/health",
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
                                "/pages/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/oauth2/authorization/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact", "/api/newsletter/subscribe").permitAll()
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
                );

        // Enable OAuth2 login when Google is configured in the environment/properties.
        String googleClientId = env.getProperty("spring.security.oauth2.client.registration.google.client-id");
        if (StringUtils.hasText(googleClientId)) {
            http = http.oauth2Login(oauth -> {
                oauth.loginPage("/login");
                oauth.authorizationEndpoint(endpoint -> endpoint.baseUri("/oauth2/authorization"));
                oauth.redirectionEndpoint(endpoint -> endpoint.baseUri("/login/oauth2/code/*"));
                oauth.userInfoEndpoint(userInfo -> userInfo.userService(googleOauth2UserService));
                oauth.successHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                    response.sendRedirect(isAdmin ? "/admin" : "/dashboard");
                });
                oauth.failureUrl("/login?error=true");
            });
        }

        http = http
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
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }
}

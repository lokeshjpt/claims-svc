package com.abc.claims.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal HTTP Basic security for the assessment.
 *
 * <p>Two in-memory users:
 * <ul>
 *   <li>{@code admin} / {@code admin123} — roles {@code ADMIN, PROCESSOR}</li>
 *   <li>{@code processor} / {@code claims123} — role {@code PROCESSOR}</li>
 * </ul>
 *
 * <p>{@code /api/**} is locked to the {@code PROCESSOR} role and is fully stateless
 * (no session, no CSRF) — appropriate for server-to-server callers and the Angular
 * SPA when running over TLS. {@code /} and {@code /process} (Thymeleaf GUI) use
 * Basic Auth too; CSRF is disabled for {@code /process} because the upload form has
 * no user-session state to protect. Static resources and health endpoints are open.
 *
 * <p><b>Future state:</b> when this monolith is decomposed into Azure-hosted
 * microservices, swap this configuration for {@code spring-boot-starter-oauth2-resource-server}
 * and validate JWTs issued by Azure Entra ID / Cognito. The role names stay the same.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/error", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasRole("PROCESSOR")
                .requestMatchers("/process", "/process/**", "/").hasRole("PROCESSOR")
                .anyRequest().authenticated())
            .httpBasic(basic -> {})
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "PROCESSOR")
                .build();
        UserDetails processor = User.builder()
                .username("processor")
                .password(encoder.encode("claims123"))
                .roles("PROCESSOR")
                .build();
        return new InMemoryUserDetailsManager(admin, processor);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

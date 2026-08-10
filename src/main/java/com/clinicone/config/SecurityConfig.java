package com.clinicone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import com.clinicone.audit.AccessAuditService;

import java.time.Clock;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<com.clinicone.auth.SessionAuthenticationFilter> sessionFilter,
                                            ObjectProvider<AccessAuditService> accessAuditService)
            throws Exception {
        var chain = http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/check-phone",
                                "/api/v1/auth/request-sms-otp",
                                "/api/v1/auth/verify-sms-otp",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login-sms",
                                "/api/v1/auth/login",
                                "/api/v1/staff/auth/login",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) -> {
                            recordAccessDenied(accessAuditService, request);
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        })
                        .authenticationEntryPoint((request, response, exception) -> {
                            recordAccessDenied(accessAuditService, request);
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        }));
        sessionFilter.ifAvailable(filter -> chain.addFilterBefore(
                filter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class));
        return chain.build();
    }

    private void recordAccessDenied(ObjectProvider<AccessAuditService> provider,
                                    jakarta.servlet.http.HttpServletRequest request) {
        AccessAuditService service = provider.getIfAvailable();
        if (service == null) return;
        var principal = request.getUserPrincipal();
        String actor = principal == null ? "ANONYMOUS" : principal.getName();
        String eventType = principal == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED";
        try {
            service.record(eventType, actor, "FAILED", request.getRequestURI(), request.getRemoteAddr());
        } catch (RuntimeException ignored) { }
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

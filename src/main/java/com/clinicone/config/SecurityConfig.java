package com.clinicone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<com.clinicone.auth.SessionAuthenticationFilter> sessionFilter)
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
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        sessionFilter.ifAvailable(filter -> chain.addFilterBefore(
                filter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class));
        return chain.build();
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

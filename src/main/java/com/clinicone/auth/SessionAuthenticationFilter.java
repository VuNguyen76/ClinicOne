package com.clinicone.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnBean(LoginSessionRepository.class)
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final LoginSessionRepository sessionRepository;
    private final Clock clock;

    public SessionAuthenticationFilter(LoginSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                LoginSession session = sessionRepository
                        .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(AccountAuthService.hashToken(token), Instant.now(clock))
                        .orElse(null);
                if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            session.getAccountId().toString(), null, List.of());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}

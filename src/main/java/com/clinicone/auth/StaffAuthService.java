package com.clinicone.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class StaffAuthService {
    private static final Duration SESSION_LIFETIME = Duration.ofHours(12);

    private final StaffAccountRepository accountRepository;
    private final LoginSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenGenerator tokenGenerator;
    private final Clock clock;

    public StaffAuthService(StaffAccountRepository accountRepository, LoginSessionRepository sessionRepository,
                            PasswordEncoder passwordEncoder, SessionTokenGenerator tokenGenerator, Clock clock) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Transactional
    public StaffLoginResponse login(StaffLoginRequest request) {
        StaffAccount account = accountRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(this::invalidCredentials);
        if (account.getStatus() != AccountStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(SESSION_LIFETIME);
        String token = tokenGenerator.generate();
        sessionRepository.save(new LoginSession(account.getId(), AccountAuthService.hashToken(token), now,
                expiresAt, account.getRole().authority()));
        return new StaffLoginResponse(token, "Bearer", expiresAt, account.getId(), account.getFullName(),
                account.getRole().name());
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "STAFF_INVALID_CREDENTIALS",
                "Tên đăng nhập hoặc mật khẩu không đúng.");
    }
}

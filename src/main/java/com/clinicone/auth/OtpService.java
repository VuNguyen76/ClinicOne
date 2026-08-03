package com.clinicone.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class OtpService {

    static final long OTP_TTL_SECONDS = 5 * 60;
    static final long RESEND_COOLDOWN_SECONDS = 60;
    static final long RATE_WINDOW_SECONDS = 15 * 60;
    static final int MAX_REQUESTS_PER_WINDOW = 5;
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final OtpChallengeRepository repository;
    private final OtpSender sender;
    private final PasswordEncoder passwordEncoder;
    private final OtpCodeGenerator codeGenerator;
    private final Clock clock;

    public OtpService(OtpChallengeRepository repository, OtpSender sender,
                      PasswordEncoder passwordEncoder, OtpCodeGenerator codeGenerator, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.passwordEncoder = passwordEncoder;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public RequestOtpResponse requestOtp(String email, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);
        Instant windowStart = now.minusSeconds(RATE_WINDOW_SECONDS);

        if (repository.countByEmailAndPurposeAndCreatedAtAfter(normalizedEmail, purpose, windowStart)
                >= MAX_REQUESTS_PER_WINDOW) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                    "Bạn đã yêu cầu quá nhiều mã. Vui lòng thử lại sau.", RATE_WINDOW_SECONDS);
        }

        var latest = repository.findTopByEmailAndPurposeOrderByCreatedAtDesc(normalizedEmail, purpose);
        if (latest.isPresent() && Duration.between(latest.get().getCreatedAt(), now).getSeconds() < RESEND_COOLDOWN_SECONDS) {
            long elapsed = Math.max(0, Duration.between(latest.get().getCreatedAt(), now).getSeconds());
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                    "Vui lòng chờ trước khi yêu cầu mã mới.", RESEND_COOLDOWN_SECONDS - elapsed);
        }

        String code = codeGenerator.generate();
        OtpChallenge challenge = new OtpChallenge(
                normalizedEmail,
                purpose,
                passwordEncoder.encode(code),
                now,
                now.plusSeconds(OTP_TTL_SECONDS)
        );
        repository.save(challenge);
        try {
            sender.send(normalizedEmail, purpose, code);
        } catch (RuntimeException ex) {
            throw new OtpException(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DELIVERY_FAILED",
                    "Không thể gửi mã xác thực lúc này. Vui lòng thử lại sau.", 60);
        }
        return new RequestOtpResponse(OTP_TTL_SECONDS, RESEND_COOLDOWN_SECONDS);
    }

    @Transactional
    public VerifyOtpResponse verifyOtp(String email, OtpPurpose purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);
        OtpChallenge challenge = repository.findTopByEmailAndPurposeOrderByCreatedAtDesc(normalizedEmail, purpose)
                .orElseThrow(this::invalidOtp);

        if (challenge.isExpired(now) || challenge.isVerified() || challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS
                || !passwordEncoder.matches(code, challenge.getCodeHash())) {
            if (!challenge.isExpired(now) && !challenge.isVerified()
                    && challenge.getFailedAttempts() < MAX_FAILED_ATTEMPTS) {
                challenge.incrementFailedAttempts();
                repository.save(challenge);
            }
            throw invalidOtp();
        }
        challenge.markVerified(now);
        repository.save(challenge);
        return new VerifyOtpResponse(true);
    }

    public boolean isRecentlyVerified(String email, OtpPurpose purpose) {
        Instant now = Instant.now(clock);
        return repository.findTopByEmailAndPurposeOrderByCreatedAtDesc(normalizeEmail(email), purpose)
                .map(challenge -> challenge.isVerified() && !challenge.isExpired(now))
                .orElse(false);
    }

    private OtpException invalidOtp() {
        return new OtpException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

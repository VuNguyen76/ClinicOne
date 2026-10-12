package com.clinicone.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OtpChallengeRetentionJob {
    private final OtpChallengeRepository repository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.auth.otp-retention-delay-ms:3600000}")
    @Transactional
    public int purgeExpiredChallenges() {
        Duration retention = Duration.ofSeconds(OtpService.OTP_TTL_SECONDS)
                .plus(OtpService.REGISTRATION_ACTIVATION_WINDOW);
        return repository.deleteCreatedBefore(Instant.now(clock).minus(retention));
    }
}

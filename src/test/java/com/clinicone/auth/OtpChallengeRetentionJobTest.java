package com.clinicone.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OtpChallengeRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private final OtpChallengeRepository repository = mock(OtpChallengeRepository.class);

    @Test
    void removesOtpDataOnlyAfterCodeAndRegistrationVerificationWindowsEnd() {
        OtpChallengeRetentionJob job = new OtpChallengeRetentionJob(repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        job.purgeExpiredChallenges();

        verify(repository).deleteCreatedBefore(NOW.minus(
                Duration.ofSeconds(OtpService.OTP_TTL_SECONDS).plus(OtpService.REGISTRATION_ACTIVATION_WINDOW)));
    }
}

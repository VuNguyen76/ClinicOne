package com.clinicone.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class OtpConcurrentVerificationIntegrationTest {
    private static final String PHONE = "0900000001";

    @Autowired
    private OtpChallengeRepository repository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpChallengeRetentionJob retentionJob;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("+84900000001", OtpPurpose.RECEIPT_LOOKUP)
                .ifPresent(repository::delete);
        repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("+84900000001", OtpPurpose.REGISTRATION)
                .ifPresent(repository::delete);
    }

    @Test
    void onlyOneConcurrentRequestCanVerifyAndConsumeTheSameOtp() throws Exception {
        Instant now = Instant.now();
        OtpChallenge challenge = new OtpChallenge("+84900000001", OtpPurpose.RECEIPT_LOOKUP,
                passwordEncoder.encode("123456"), now, now.plusSeconds(300));
        repository.saveAndFlush(challenge);

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var attempt = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    return otpService.verifySmsOtp(PHONE, OtpPurpose.RECEIPT_LOOKUP, "123456").verified();
                } catch (OtpException exception) {
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertTrue(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc(
                    "+84900000001", OtpPurpose.RECEIPT_LOOKUP).orElseThrow().isVerified());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyOneConcurrentRequestCanIssueOtpDuringTheResendCooldown() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var attempt = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    otpService.requestSmsOtp(PHONE, OtpPurpose.REGISTRATION);
                    return true;
                } catch (OtpException exception) {
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, repository.findAll().stream()
                    .filter(challenge -> challenge.getDestination().equals("+84900000001")
                            && challenge.getPurpose() == OtpPurpose.REGISTRATION)
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rateLimitCountsRequestsAcrossPurposesAndRetentionPurgesOnlyOldChallenges() {
        Instant now = Instant.now();
        OtpChallenge oldChallenge = new OtpChallenge("+84900000001", OtpPurpose.REGISTRATION,
                passwordEncoder.encode("111111"), now.minus(Duration.ofMinutes(36)),
                now.minus(Duration.ofMinutes(31)));
        OtpChallenge recentRegistration = new OtpChallenge("+84900000001", OtpPurpose.REGISTRATION,
                passwordEncoder.encode("222222"), now, now.plusSeconds(300));
        OtpChallenge recentReceiptLookup = new OtpChallenge("+84900000001", OtpPurpose.RECEIPT_LOOKUP,
                passwordEncoder.encode("333333"), now, now.plusSeconds(300));
        repository.saveAllAndFlush(List.of(oldChallenge, recentRegistration, recentReceiptLookup));

        assertEquals(2, repository.countByDestinationAndCreatedAtAfter("+84900000001", now.minusSeconds(1)));

        retentionJob.purgeExpiredChallenges();

        assertTrue(repository.findById(oldChallenge.getId()).isEmpty());
        assertTrue(repository.findById(recentRegistration.getId()).isPresent());
        assertTrue(repository.findById(recentReceiptLookup.getId()).isPresent());
    }
}

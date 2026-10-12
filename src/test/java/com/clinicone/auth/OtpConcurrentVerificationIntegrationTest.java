package com.clinicone.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
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
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("+84900000001", OtpPurpose.RECEIPT_LOOKUP)
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
}

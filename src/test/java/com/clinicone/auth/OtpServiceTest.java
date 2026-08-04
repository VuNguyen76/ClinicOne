package com.clinicone.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    private OtpChallengeRepository repository;
    private OtpSender sender;
    private OtpCodeGenerator generator;
    private PasswordEncoder passwordEncoder;
    private OtpService service;

    @BeforeEach
    void setUp() {
        repository = mock(OtpChallengeRepository.class);
        sender = mock(OtpSender.class);
        generator = mock(OtpCodeGenerator.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new OtpService(repository, sender, passwordEncoder, generator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestOtpStoresOnlyHashAndSendsCode() {
        when(repository.countByDestinationAndPurposeAndCreatedAtAfter(eq("user@example.com"),
                eq(OtpPurpose.REGISTRATION), any())).thenReturn(0L);
        when(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());
        when(generator.generate()).thenReturn("123456");

        RequestOtpResponse response = service.requestOtp(" USER@example.com ", OtpPurpose.REGISTRATION);

        assertEquals(300, response.expiresInSeconds());
        assertEquals(60, response.retryAfterSeconds());
        verify(sender).send("user@example.com", OtpPurpose.REGISTRATION, "123456");
        ArgumentCaptor<OtpChallenge> challengeCaptor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(repository).save(challengeCaptor.capture());
        assertFalse(challengeCaptor.getValue().getCodeHash().equals("123456"));
    }

    @Test
    void requestOtpRejectsResendDuringCooldown() {
        OtpChallenge previous = new OtpChallenge("user@example.com", OtpPurpose.REGISTRATION,
                "hash", NOW.minusSeconds(30), NOW.plusSeconds(270));
        when(repository.countByDestinationAndPurposeAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        when(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(previous));

        OtpException exception = assertThrows(OtpException.class,
                () -> service.requestOtp("user@example.com", OtpPurpose.REGISTRATION));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("OTP_COOLDOWN", exception.getCode());
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void verifyOtpMarksLatestChallengeAsVerified() {
        OtpChallenge challenge = new OtpChallenge("user@example.com", OtpPurpose.LOGIN,
                passwordEncoder.encode("123456"), NOW.minusSeconds(10), NOW.plusSeconds(290));
        when(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("user@example.com", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(challenge));

        VerifyOtpResponse response = service.verifyOtp("user@example.com", OtpPurpose.LOGIN, "123456");

        assertTrue(response.verified());
        assertTrue(challenge.isVerified());
        verify(repository).save(challenge);
    }

    @Test
    void verifyOtpCountsWrongCodeAndReturnsGenericError() {
        OtpChallenge challenge = new OtpChallenge("user@example.com", OtpPurpose.LOGIN,
                passwordEncoder.encode("123456"), NOW.minusSeconds(10), NOW.plusSeconds(290));
        when(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("user@example.com", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(challenge));

        OtpException exception = assertThrows(OtpException.class,
                () -> service.verifyOtp("user@example.com", OtpPurpose.LOGIN, "000000"));

        assertEquals("OTP_INVALID", exception.getCode());
        assertEquals(1, challenge.getFailedAttempts());
        assertFalse(challenge.isVerified());
        verify(repository).save(challenge);
    }

    @Test
    void requestSmsOtpConvertsVietnamesePhoneToE164() {
        when(repository.countByDestinationAndPurposeAndCreatedAtAfter(eq("+84912345678"),
                eq(OtpPurpose.REGISTRATION), any())).thenReturn(0L);
        when(repository.findTopByDestinationAndPurposeOrderByCreatedAtDesc("+84912345678", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());
        when(generator.generate()).thenReturn("654321");

        service.requestSmsOtp("0912 345 678", OtpPurpose.REGISTRATION);

        verify(sender).send("+84912345678", OtpPurpose.REGISTRATION, "654321");
    }
}

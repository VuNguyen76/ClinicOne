package com.clinicone.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private PatientAccountRepository accountRepository;
    private LoginSessionRepository sessionRepository;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private SessionTokenGenerator tokenGenerator;
    private AccountAuthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        sessionRepository = mock(LoginSessionRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenGenerator = mock(SessionTokenGenerator.class);
        service = new AccountAuthService(accountRepository, sessionRepository, otpService, passwordEncoder,
                tokenGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountRepository.save(any(PatientAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(LoginSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersOnlyAfterRegistrationEmailWasVerified() {
        when(otpService.isRecentlyVerified("user@example.com", OtpPurpose.REGISTRATION)).thenReturn(true);
        when(accountRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        PatientAccount saved = new PatientAccount("user@example.com", "0912345678", "password-hash",
                "Nguyen Van A", NOW, AccountStatus.ACTIVE, false);
        setId(saved, ACCOUNT_ID);
        when(accountRepository.save(any(PatientAccount.class))).thenReturn(saved);

        RegistrationResponse response = service.register(new RegistrationRequest(
                " USER@example.com ", "0912345678", "Nguyen Van A", "password123"));

        assertEquals(ACCOUNT_ID, response.accountId());
        assertEquals("user@example.com", response.email());
        verify(accountRepository).save(any(PatientAccount.class));
    }

    @Test
    void rejectsRegistrationWithoutVerifiedOtp() {
        when(otpService.isRecentlyVerified("user@example.com", OtpPurpose.REGISTRATION)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> service.register(new RegistrationRequest(
                "user@example.com", "0912345678", "Nguyen Van A", "password123")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("EMAIL_NOT_VERIFIED", exception.getCode());
    }

    @Test
    void loginCreatesOpaqueSessionToken() {
        PatientAccount account = new PatientAccount("user@example.com", "0912345678", "password-hash",
                "Nguyen Van A", NOW, AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "password-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-session-token");

        LoginResponse response = service.login(new LoginRequest(" USER@example.com ", "password123"));

        assertEquals("raw-session-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(ACCOUNT_ID, response.accountId());
        assertFalse(response.mustChangePassword());
        verify(sessionRepository).save(any(LoginSession.class));
    }

    @Test
    void lockedAccountCannotLogin() {
        PatientAccount account = new PatientAccount("user@example.com", "0912345678", "password-hash",
                "Nguyen Van A", NOW, AccountStatus.LOCKED, false);
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.login(new LoginRequest("user@example.com", "password123")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getCode());
    }

    @Test
    void changingPasswordClearsTemporaryPasswordFlag() {
        PatientAccount account = new PatientAccount("user@example.com", "0912345678", "temporary-hash",
                "Nguyen Van A", NOW, AccountStatus.ACTIVE, true);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "temporary-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.changePassword(ACCOUNT_ID.toString(), new ChangePasswordRequest("123456", "new-password"));

        assertFalse(account.isMustChangePassword());
        assertEquals("new-hash", account.getPasswordHash());
        verify(accountRepository).save(account);
    }

    private static void setId(PatientAccount account, UUID id) {
        try {
            Field field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

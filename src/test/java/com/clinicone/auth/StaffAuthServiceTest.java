package com.clinicone.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StaffAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");
    private static final UUID STAFF_ID = UUID.fromString("ed9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private StaffAccountRepository accountRepository;
    private LoginSessionRepository sessionRepository;
    private PasswordEncoder passwordEncoder;
    private SessionTokenGenerator tokenGenerator;
    private StaffAuthService service;
    private StaffAccount admin;

    @BeforeEach
    void setUp() {
        accountRepository = mock(StaffAccountRepository.class);
        sessionRepository = mock(LoginSessionRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenGenerator = mock(SessionTokenGenerator.class);
        service = new StaffAuthService(accountRepository, sessionRepository, passwordEncoder, tokenGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        admin = StaffAccount.create("admin", "password-hash", "Quản trị viên", StaffRole.ADMIN);
        setId(admin, STAFF_ID);
        when(sessionRepository.save(any(LoginSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activeStaffLoginCreatesRoleBearingSession() {
        when(accountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", "password-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("staff-session-token");

        StaffLoginResponse response = service.login(new StaffLoginRequest("admin", "password123"));

        assertEquals("staff-session-token", response.accessToken());
        assertEquals("ADMIN", response.role());
        assertEquals(STAFF_ID, response.staffId());
        verify(sessionRepository).save(argThat(session -> "ROLE_ADMIN".equals(session.getRole())));
    }

    @Test
    void lockedStaffCannotLogin() {
        StaffAccount locked = StaffAccount.create("admin", "password-hash", "Quản trị viên", StaffRole.ADMIN);
        locked.lock();
        when(accountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(locked));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.login(new StaffLoginRequest("admin", "password123")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("STAFF_INVALID_CREDENTIALS", exception.getCode());
        verifyNoInteractions(passwordEncoder, sessionRepository);
    }

    @Test
    void wrongPasswordDoesNotCreateSession() {
        when(accountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "password-hash")).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.login(new StaffLoginRequest("admin", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verifyNoInteractions(sessionRepository);
    }

    private static void setId(StaffAccount target, UUID id) {
        try {
            var field = StaffAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

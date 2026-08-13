package com.clinicone.auth;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.notification.PatientNotificationBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private PatientProfileRepository patientProfileRepository;
    private AppointmentRepository appointmentRepository;
    private AccountAuthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        sessionRepository = mock(LoginSessionRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenGenerator = mock(SessionTokenGenerator.class);
        patientProfileRepository = mock(PatientProfileRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        service = new AccountAuthService(accountRepository, sessionRepository, otpService, passwordEncoder,
                tokenGenerator, Clock.fixed(NOW, ZoneOffset.UTC), patientProfileRepository);
        when(accountRepository.save(any(PatientAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(LoginSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersOnlyAfterRegistrationPhoneWasVerified() {
        when(otpService.isPhoneRecentlyVerified("0912345678", OtpPurpose.REGISTRATION)).thenReturn(true);
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        PatientAccount saved = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(saved, ACCOUNT_ID);
        when(accountRepository.save(any(PatientAccount.class))).thenReturn(saved);

        RegistrationResponse response = service.register(new RegistrationRequest(
                "0912345678", "Nguyen Van A", "password123"));

        assertEquals(ACCOUNT_ID, response.accountId());
        assertEquals("0912345678", response.phone());
        verify(accountRepository).save(any(PatientAccount.class));
    }

    @Test
    void registrationCreatesThePrimaryPatientProfile() {
        when(otpService.isPhoneRecentlyVerified("0912345678", OtpPurpose.REGISTRATION)).thenReturn(true);
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        PatientAccount saved = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(saved, ACCOUNT_ID);
        saved.updateProfile("Nguyen Van A", LocalDate.of(2000, 1, 1), "Nam", "Tay Ninh");
        when(accountRepository.save(any(PatientAccount.class))).thenReturn(saved);

        service.register(new RegistrationRequest("0912345678", "Nguyen Van A", "password123",
                LocalDate.of(2000, 1, 1), "Nam", "Tay Ninh"));

        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    void rejectsRegistrationWithoutVerifiedOtp() {
        when(otpService.isPhoneRecentlyVerified("0912345678", OtpPurpose.REGISTRATION)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> service.register(new RegistrationRequest(
                "0912345678", "Nguyen Van A", "password123")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("PHONE_NOT_VERIFIED", exception.getCode());
    }

    @Test
    void loginCreatesOpaqueSessionToken() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(otpService.verifySmsOtp("0912345678", OtpPurpose.LOGIN, "123456"))
                .thenReturn(new VerifyOtpResponse(true));
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "password-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-session-token");

        LoginResponse response = service.loginBySmsOtp(new SmsLoginRequest("0912345678", "password123", "123456"));

        assertEquals("raw-session-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(ACCOUNT_ID, response.accountId());
        assertFalse(response.mustChangePassword());
        verify(sessionRepository).save(any(LoginSession.class));
    }

    @Test
    void lockedAccountCannotLogin() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.LOCKED, false);
        when(otpService.verifySmsOtp("0912345678", OtpPurpose.LOGIN, "123456"))
                .thenReturn(new VerifyOtpResponse(true));
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.loginBySmsOtp(new SmsLoginRequest("0912345678", "password123", "123456")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getCode());
    }

    @Test
    void fifthWrongPasswordTemporarilyLocksAccountAndRevokesSessions() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        for (int attempt = 1; attempt <= 5; attempt++) {
            AuthException exception = assertThrows(AuthException.class,
                    () -> service.login(new PasswordLoginRequest("0912345678", "wrong-password")));
            assertEquals(attempt == 5 ? "ACCOUNT_TEMPORARILY_LOCKED" : "AUTH_INVALID_CREDENTIALS", exception.getCode());
        }

        assertEquals(AccountStatus.LOCKED, account.getStatus());
        assertTrue(account.getLockedUntil().isAfter(NOW));
        verify(sessionRepository).revokeActiveByAccountId(eq(ACCOUNT_ID), eq(NOW));
        verify(accountRepository, org.mockito.Mockito.times(5)).save(account);
    }

    @Test
    void expiredTemporaryLockReopensAccountForSuccessfulLogin() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        account.recordPasswordFailure(NOW);
        account.recordPasswordFailure(NOW.plusSeconds(1));
        account.recordPasswordFailure(NOW.plusSeconds(2));
        account.recordPasswordFailure(NOW.plusSeconds(3));
        account.recordPasswordFailure(NOW.plusSeconds(4));
        Clock afterLock = Clock.fixed(NOW.plusSeconds(15 * 60 + 5), ZoneOffset.UTC);
        AccountAuthService afterLockService = new AccountAuthService(accountRepository, sessionRepository, otpService,
                passwordEncoder, tokenGenerator, afterLock, patientProfileRepository);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "password-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-session-token");

        LoginResponse response = afterLockService.login(new PasswordLoginRequest("0912345678", "password123"));

        assertEquals("raw-session-token", response.accessToken());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getFailedPasswordAttempts());
    }

    @Test
    void recoveryOtpResetsPasswordUnlocksAccountAndRevokesSessions() {
        PatientAccount account = new PatientAccount("0912345678", "old-hash", "Nguyen Van A", AccountStatus.LOCKED, false);
        setId(account, ACCOUNT_ID);
        account.recordPasswordFailure(NOW);
        account.recordPasswordFailure(NOW.plusSeconds(1));
        account.recordPasswordFailure(NOW.plusSeconds(2));
        account.recordPasswordFailure(NOW.plusSeconds(3));
        account.recordPasswordFailure(NOW.plusSeconds(4));
        when(otpService.isPhoneRecentlyVerified("0912345678", OtpPurpose.RECOVERY)).thenReturn(true);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword(new ResetPasswordRequest("0912345678", "new-password", "new-password"));

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals("new-hash", account.getPasswordHash());
        assertEquals(0, account.getFailedPasswordAttempts());
        verify(sessionRepository).revokeActiveByAccountId(ACCOUNT_ID, NOW);
    }

    @Test
    void recoveryRequiresVerifiedRecoveryOtp() {
        when(otpService.isPhoneRecentlyVerified("0912345678", OtpPurpose.RECOVERY)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> service.resetPassword(
                new ResetPasswordRequest("0912345678", "new-password", "new-password")));

        assertEquals("RECOVERY_OTP_REQUIRED", exception.getCode());
        verify(accountRepository, org.mockito.Mockito.never()).save(any(PatientAccount.class));
    }

    @Test
    void locksAlsoCreateOneSecurityNotificationRequest() {
        PatientNotificationService notifications = mock(PatientNotificationService.class);
        AccountAuthService notifyingService = new AccountAuthService(accountRepository, sessionRepository, otpService,
                passwordEncoder, tokenGenerator, Clock.fixed(NOW, ZoneOffset.UTC), patientProfileRepository,
                appointmentRepository, notifications);
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthException.class, () -> notifyingService.login(
                    new PasswordLoginRequest("0912345678", "wrong-password")));
        }

        verify(notifications).notifyAccountSecurityLocked(eq(ACCOUNT_ID), eq(account.getLockedUntil()));
    }

    @Test
    void changingPasswordClearsTemporaryPasswordFlag() {
        PatientAccount account = new PatientAccount("0912345678", "temporary-hash", "Nguyen Van A", AccountStatus.ACTIVE, true);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "temporary-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.changePassword(ACCOUNT_ID.toString(), new ChangePasswordRequest("123456", "new-password"));

        assertFalse(account.isMustChangePassword());
        assertEquals("new-hash", account.getPasswordHash());
        verify(accountRepository).save(account);
    }

    @Test
    void activatesPendingAccountAfterRecentReceptionOtp() {
        PatientAccount account = new PatientAccount("0912345678", "pending-hash", "Nguyen Van A", AccountStatus.ACTIVE, true);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(otpService.isPhoneVerifiedWithin("0912345678", OtpPurpose.REGISTRATION,
                Duration.ofMinutes(30))).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.activatePendingAccount(new ActivateAccountRequest("0912345678", "new-password", "new-password"));

        assertFalse(account.isMustChangePassword());
        assertEquals("new-hash", account.getPasswordHash());
        verify(accountRepository).save(account);
    }

    @Test
    void linksTemporaryReceptionProfilesOnlyAfterPendingAccountActivation() {
        PatientAccount account = new PatientAccount("0912345678", "pending-hash", "Nguyen Van A",
                AccountStatus.ACTIVE, true);
        setId(account, ACCOUNT_ID);
        PatientProfile temporary = PatientProfile.createTemporary("Nguyen Van A", LocalDate.of(2000, 1, 1),
                "Nam", "0912345678", null, null, null, null);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(otpService.isPhoneVerifiedWithin("0912345678", OtpPurpose.REGISTRATION,
                Duration.ofMinutes(30))).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(patientProfileRepository.findByTemporaryProfileTrueAndOwnerIsNullAndPhone("0912345678"))
                .thenReturn(List.of(temporary));

        service.activatePendingAccount(new ActivateAccountRequest("0912345678", "new-password", "new-password"));

        assertFalse(temporary.isTemporaryProfile());
        assertEquals(account, temporary.getOwner());
        verify(patientProfileRepository).save(temporary);
    }

    @Test
    void linksTemporaryAppointmentsWhenReceptionProfileBecomesOwned() {
        PatientAccount account = new PatientAccount("0912345678", "pending-hash", "Nguyen Van A",
                AccountStatus.ACTIVE, true);
        setId(account, ACCOUNT_ID);
        PatientProfile temporary = PatientProfile.createTemporary("Nguyen Van A", LocalDate.of(2000, 1, 1),
                "Nam", "0912345678", null, null, null, null);
        UUID profileId = UUID.fromString("f29ef2d6-a6ac-4380-bc01-17f97a6f5c40");
        setId(temporary, profileId);
        Appointment appointment = Appointment.createTemporary(temporary, null, "APT-TEMP-001", "Nội tổng quát",
                "Bác sĩ A", LocalDate.of(2026, 8, 12), LocalTime.of(8, 30), "Khám tổng quát");
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(otpService.isPhoneVerifiedWithin("0912345678", OtpPurpose.REGISTRATION,
                Duration.ofMinutes(30))).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(patientProfileRepository.findByTemporaryProfileTrueAndOwnerIsNullAndPhone("0912345678"))
                .thenReturn(List.of(temporary));
        when(appointmentRepository.findByPatientProfileId(profileId)).thenReturn(List.of(appointment));
        PatientNotificationBackfillService backfillService = mock(PatientNotificationBackfillService.class);
        AccountAuthService linkingService = new AccountAuthService(accountRepository, sessionRepository, otpService,
                passwordEncoder, tokenGenerator, Clock.fixed(NOW, ZoneOffset.UTC), patientProfileRepository,
                appointmentRepository, null, backfillService);

        linkingService.activatePendingAccount(new ActivateAccountRequest("0912345678", "new-password", "new-password"));

        assertEquals(account, appointment.getPatient());
        verify(appointmentRepository).save(appointment);
        verify(backfillService).notifyRecentSignedRecords(ACCOUNT_ID, profileId);
    }

    @Test
    void rejectsPendingActivationWhenOtpWindowHasExpired() {
        PatientAccount account = new PatientAccount("0912345678", "pending-hash", "Nguyen Van A", AccountStatus.ACTIVE, true);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));
        when(otpService.isPhoneVerifiedWithin("0912345678", OtpPurpose.REGISTRATION,
                Duration.ofMinutes(30))).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> service.activatePendingAccount(
                new ActivateAccountRequest("0912345678", "new-password", "new-password")));

        assertEquals("ACTIVATION_OTP_REQUIRED", exception.getCode());
    }

    @Test
    void updatesFullNameWithoutChangingPhone() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        PatientProfileResponse response = service.updateProfile(ACCOUNT_ID.toString(), new UpdateProfileRequest("Nguyen Thi B", LocalDate.of(2005, 6, 7), "Nam", "Tay Ninh"));

        assertEquals("Nguyen Thi B", response.fullName());
        assertEquals("0912345678", response.phone());
        assertEquals(LocalDate.of(2005, 6, 7), response.dateOfBirth());
        assertEquals("Nam", response.gender());
        assertEquals("Tay Ninh", response.address());
        verify(accountRepository).save(account);
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    void rejectsProfileDetailsOutsideTheClinicCatalog() {
        PatientAccount account = new PatientAccount("0912345678", "password-hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        AuthException exception = assertThrows(AuthException.class, () -> service.updateProfile(ACCOUNT_ID.toString(),
                new UpdateProfileRequest("Nguyen Van A", LocalDate.of(1899, 12, 31), "Unknown", "Tay Ninh")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(accountRepository, org.mockito.Mockito.never()).save(any(PatientAccount.class));
    }

    @Test
    void logoutRevokesAllActiveSessions() {
        service.logout(ACCOUNT_ID.toString());

        verify(sessionRepository).revokeActiveByAccountId(eq(ACCOUNT_ID), eq(NOW));
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

    private static void setId(PatientProfile profile, UUID id) {
        try {
            Field field = PatientProfile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(profile, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

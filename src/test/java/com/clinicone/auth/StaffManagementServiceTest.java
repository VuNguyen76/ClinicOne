package com.clinicone.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffManagementServiceTest {
    private static final UUID STAFF_ID = UUID.fromString("0b6f0f1a-11cd-4c96-98f8-2d46c9eae2c1");
    private final StaffAccountRepository accountRepository = mock(StaffAccountRepository.class);
    private final LoginSessionRepository sessionRepository = mock(LoginSessionRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private StaffManagementService service;

    @BeforeEach
    void setUp() {
        service = new StaffManagementService(accountRepository, sessionRepository,
                Clock.fixed(Instant.parse("2026-08-10T05:00:00Z"), ZoneOffset.UTC), passwordEncoder);
        when(accountRepository.save(any(StaffAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lockingStaffRevokesEveryOpenSessionImmediately() throws Exception {
        StaffAccount account = account();
        when(accountRepository.findById(STAFF_ID)).thenReturn(Optional.of(account));

        StaffAccountResponse response = service.lock(STAFF_ID, "admin");

        assertEquals(AccountStatus.LOCKED, response.status());
        assertEquals(AccountStatus.LOCKED, account.getStatus());
        verify(sessionRepository).revokeActiveByAccountId(eq(STAFF_ID), any(Instant.class));
    }

    @Test
    void unlockingStaffMakesTheAccountAvailableWithoutCreatingASession() throws Exception {
        StaffAccount account = account();
        account.lock();
        when(accountRepository.findById(STAFF_ID)).thenReturn(Optional.of(account));

        StaffAccountResponse response = service.unlock(STAFF_ID, "admin");

        assertEquals(AccountStatus.ACTIVE, response.status());
        verify(accountRepository).save(account);
    }

    @Test
    void unknownStaffAccountIsRejected() {
        when(accountRepository.findById(STAFF_ID)).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> service.lock(STAFF_ID, "admin"));
    }

    @Test
    void createsStaffWithEmployeeIdentityAndMultipleBusinessRoles() {
        when(accountRepository.existsByEmployeeCodeIgnoreCase("NV001")).thenReturn(false);
        when(accountRepository.findByUsernameIgnoreCase("NV001")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-initial-password");

        StaffAccountCreatedResponse response = service.create(new CreateStaffAccountRequest(
                "Bác sĩ Nguyễn An", "NV001", "ClinicOne", "Khoa Nội",
                List.of(StaffRole.DOCTOR, StaffRole.COORDINATOR)));

        assertEquals("NV001", response.account().employeeCode());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(StaffRole.DOCTOR, StaffRole.COORDINATOR), response.account().roles());
        verify(accountRepository).save(any(StaffAccount.class));
    }

    @Test
    void rejectsCombiningAdminWithBusinessRole() {
        AuthException exception = assertThrows(AuthException.class, () -> service.create(new CreateStaffAccountRequest(
                "Quản trị viên", "AD001", "ClinicOne", "Quản trị",
                List.of(StaffRole.ADMIN, StaffRole.DOCTOR))));

        assertEquals("STAFF_ROLE_COMBINATION_INVALID", exception.getCode());
        verify(accountRepository, never()).save(any(StaffAccount.class));
    }

    @Test
    void updatesBusinessRolesWithoutAllowingAdminCombination() throws Exception {
        StaffAccount account = account();
        when(accountRepository.findById(STAFF_ID)).thenReturn(Optional.of(account));

        StaffAccountResponse response = service.updateRoles(STAFF_ID,
                new UpdateStaffRolesRequest(List.of(StaffRole.DOCTOR, StaffRole.RECEPTIONIST)));

        assertEquals(java.util.Set.of(StaffRole.DOCTOR, StaffRole.RECEPTIONIST), response.roles());
        assertEquals(java.util.Set.of(StaffRole.DOCTOR, StaffRole.RECEPTIONIST), account.getRoles());
        verify(accountRepository).save(account);
        verify(sessionRepository).revokeActiveByAccountId(eq(STAFF_ID), any(Instant.class));
    }

    @Test
    void rejectsAdminCombinedWithBusinessRoleWhenUpdating() throws Exception {
        AuthException exception = assertThrows(AuthException.class, () -> service.updateRoles(STAFF_ID,
                new UpdateStaffRolesRequest(List.of(StaffRole.ADMIN, StaffRole.DOCTOR))));

        assertEquals("STAFF_ROLE_COMBINATION_INVALID", exception.getCode());
        verify(accountRepository, never()).findById(any(UUID.class));
    }

    private StaffAccount account() throws Exception {
        StaffAccount account = StaffAccount.create("bs.an", "hash", "Bác sĩ An", StaffRole.DOCTOR);
        Field field = StaffAccount.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(account, STAFF_ID);
        return account;
    }
}

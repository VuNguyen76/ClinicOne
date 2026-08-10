package com.clinicone.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffManagementServiceTest {
    private static final UUID STAFF_ID = UUID.fromString("0b6f0f1a-11cd-4c96-98f8-2d46c9eae2c1");
    private final StaffAccountRepository accountRepository = mock(StaffAccountRepository.class);
    private final LoginSessionRepository sessionRepository = mock(LoginSessionRepository.class);
    private StaffManagementService service;

    @BeforeEach
    void setUp() {
        service = new StaffManagementService(accountRepository, sessionRepository,
                Clock.fixed(Instant.parse("2026-08-10T05:00:00Z"), ZoneOffset.UTC));
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

    private StaffAccount account() throws Exception {
        StaffAccount account = StaffAccount.create("bs.an", "hash", "Bác sĩ An", StaffRole.DOCTOR);
        Field field = StaffAccount.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(account, STAFF_ID);
        return account;
    }
}

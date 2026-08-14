package com.clinicone.schedule;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.appointment.CreateAppointmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentHoldServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID DOCTOR_ID = UUID.fromString("c5e4f7d2-9d7a-4bcb-95c7-1d9a8c1e31d5");
    private static final Instant NOW = Instant.parse("2026-08-10T01:00:00Z");

    private PatientAccountRepository accountRepository;
    private AppointmentHoldRepository holdRepository;
    private AppointmentAvailabilityService availabilityService;
    private AppointmentHoldService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        holdRepository = mock(AppointmentHoldRepository.class);
        availabilityService = mock(AppointmentAvailabilityService.class);
        service = new AppointmentHoldService(accountRepository, holdRepository, availabilityService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsConfiguredTenMinuteHoldForAuthenticatedPatient() {
        PatientAccount account = account();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByHoldKey(any())).thenReturn(Optional.empty());
        when(holdRepository.saveAndFlush(any(AppointmentHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentHoldResponse response = service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentHoldRequest("Nội tổng quát", "BS. An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), DOCTOR_ID));

        assertEquals(NOW.plusSeconds(600), response.expiresAt());
        verify(availabilityService).ensureBookable("Nội tổng quát", "BS. An", DOCTOR_ID,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30));
    }

    @Test
    void keepsSelectedClinicServiceOnHold() {
        PatientAccount account = account();
        UUID serviceId = UUID.randomUUID();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByHoldKey(any())).thenReturn(Optional.empty());
        when(holdRepository.saveAndFlush(any(AppointmentHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentHoldResponse response = service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentHoldRequest("Nội tổng quát", "BS. An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), DOCTOR_ID, serviceId));

        assertEquals(serviceId, response.serviceId());
    }

    @Test
    void releasesThePatientsPreviousActiveHoldBeforeCreatingAnotherSlot() {
        PatientAccount account = account();
        AppointmentHold previous = AppointmentHold.create(account, "Nội tổng quát", "BS. Cũ", DOCTOR_ID,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "DOCTOR:old:2026-08-10:08:30",
                NOW.plusSeconds(120));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByHoldKey(any())).thenReturn(Optional.empty());
        when(holdRepository.findByPatientIdAndExpiresAtAfter(ACCOUNT_ID, NOW)).thenReturn(List.of(previous));
        when(holdRepository.saveAndFlush(any(AppointmentHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(ACCOUNT_ID.toString(), new CreateAppointmentHoldRequest("Nội tổng quát", "BS. Mới",
                LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), DOCTOR_ID));

        verify(holdRepository).delete(previous);
    }

    @Test
    void reusesAnActiveHoldOwnedBySamePatient() {
        PatientAccount account = account();
        AppointmentHold existing = AppointmentHold.create(account, "Nội tổng quát", "BS. An", DOCTOR_ID,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "DOCTOR:" + DOCTOR_ID + ":2026-08-10:08:30",
                NOW.plusSeconds(120));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByHoldKey(any())).thenReturn(Optional.of(existing));

        AppointmentHoldResponse response = service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentHoldRequest("Nội tổng quát", "BS. An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), DOCTOR_ID));

        assertEquals(existing.getId(), response.id());
        verify(holdRepository, never()).save(any(AppointmentHold.class));
    }

    @Test
    void rejectsAnActiveHoldOwnedByAnotherPatient() {
        PatientAccount account = account();
        PatientAccount other = new PatientAccount("0987654321", "hash", "Nguyen Van B", AccountStatus.ACTIVE, false);
        setAccountId(other, UUID.fromString("49ea6f4b-c1e6-4d5b-ae2c-3b4f7079ce15"));
        AppointmentHold existing = AppointmentHold.create(other, "Nội tổng quát", "BS. An", DOCTOR_ID,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "DOCTOR:" + DOCTOR_ID + ":2026-08-10:08:30",
                NOW.plusSeconds(120));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByHoldKey(any())).thenReturn(Optional.of(existing));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentHoldRequest("Nội tổng quát", "BS. An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), DOCTOR_ID)));

        assertEquals("APPOINTMENT_SLOT_HELD", exception.getCode());
    }

    @Test
    void rejectsExpiredHoldWhenBooking() throws Exception {
        PatientAccount account = account();
        AppointmentHold hold = AppointmentHold.create(account, "Nội tổng quát", "BS. An", DOCTOR_ID,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "DOCTOR:" + DOCTOR_ID + ":2026-08-10:08:30",
                NOW.minusSeconds(1));
        setId(hold, UUID.randomUUID());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(holdRepository.findByIdAndPatientId(hold.getId(), ACCOUNT_ID)).thenReturn(Optional.of(hold));

        AuthException exception = assertThrows(AuthException.class, () -> service.requireForBooking(
                ACCOUNT_ID.toString(), hold.getId(), new CreateAppointmentRequest("Nội tổng quát", "BS. An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu", null, DOCTOR_ID, hold.getId())));

        assertEquals("APPOINTMENT_HOLD_EXPIRED", exception.getCode());
        verify(holdRepository).delete(hold);
    }

    private PatientAccount account() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        try {
            Field field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, ACCOUNT_ID);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return account;
    }

    private static void setId(AppointmentHold hold, UUID id) throws Exception {
        Field field = AppointmentHold.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(hold, id);
    }

    private static void setAccountId(PatientAccount account, UUID id) {
        try {
            Field field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

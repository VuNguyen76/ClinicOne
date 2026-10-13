package com.clinicone.appointment;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorAppointmentsServiceTest {
    private static final UUID DOCTOR_ID = UUID.fromString("8131b3ba-b830-4616-80cf-07a04e49ace2");
    private final AppointmentRepository repository = mock(AppointmentRepository.class);
    private final DoctorAppointmentsService service = new DoctorAppointmentsService(repository);

    @Test
    void returnsOnlyAppointmentsForTheRequestedDoctorWithinDateRange() {
        LocalDate from = LocalDate.parse("2026-10-13");
        LocalDate to = LocalDate.parse("2026-11-12");
        when(repository.findByDoctorStaffIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                DOCTOR_ID, from, to)).thenReturn(List.of());

        assertEquals(List.of(), service.list(DOCTOR_ID, from, to));
        verify(repository).findByDoctorStaffIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                DOCTOR_ID, from, to);
    }

    @Test
    void rejectsInvalidOrUnboundedDateRanges() {
        assertThrows(AuthException.class, () -> service.list(DOCTOR_ID,
                LocalDate.parse("2026-11-12"), LocalDate.parse("2026-10-13")));
        assertThrows(AuthException.class, () -> service.list(DOCTOR_ID,
                LocalDate.parse("2026-10-13"), LocalDate.parse("2027-01-13")));
    }
}

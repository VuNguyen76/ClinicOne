package com.clinicone.schedule;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentAvailabilityServiceTest {
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final AppointmentAvailabilityService service = new AppointmentAvailabilityService(appointmentRepository);

    @Test
    void returnsOnlyWorkingDaySlotsForKnownSpecialty() {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        when(appointmentRepository.countBookedBySpecialtyAndDateRange(
                "Khám Tổng Quát", monday, monday, AppointmentStatus.BOOKED))
                .thenReturn(List.of(new SlotBookingCount(monday, LocalTime.of(7, 30), 2L)));

        List<AvailableSlotResponse> slots = service.find("Khám Tổng Quát", monday, monday);

        assertEquals(7, slots.size());
        assertEquals(8, slots.get(0).remainingCapacity());
        assertEquals(LocalTime.of(7, 30), slots.get(0).startTime());
        verify(appointmentRepository).countBookedBySpecialtyAndDateRange(
                "Khám Tổng Quát", monday, monday, AppointmentStatus.BOOKED);
    }

    @Test
    void rejectsUnknownSpecialty() {
        assertThrows(AuthException.class, () -> service.find("Không tồn tại", LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 10)));
    }

    @Test
    void doesNotReturnSundaySlots() {
        LocalDate sunday = LocalDate.of(2026, 8, 9);
        assertEquals(List.of(), service.find("Khám Tổng Quát", sunday, sunday));
    }
}

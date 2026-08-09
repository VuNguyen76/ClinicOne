package com.clinicone.schedule;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoom;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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

    @Test
    void loadsConfiguredMonthWithBatchQueries() {
        DoctorProfileRepository doctorProfileRepository = mock(DoctorProfileRepository.class);
        DoctorScheduleRepository scheduleRepository = mock(DoctorScheduleRepository.class);
        AppointmentAvailabilityService configuredService = new AppointmentAvailabilityService(
                appointmentRepository, new SpecialtyCatalogService(), doctorProfileRepository, scheduleRepository);

        UUID doctorStaffId = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        DoctorProfile profile = mock(DoctorProfile.class);
        DoctorSchedule schedule = mock(DoctorSchedule.class);
        when(profile.getStaffAccount()).thenReturn(staff);
        when(profile.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(profile.getRoom()).thenReturn(room);
        when(staff.getId()).thenReturn(doctorStaffId);
        when(staff.getFullName()).thenReturn("Bác sĩ An");
        when(room.getCode()).thenReturn("NOI-01");
        when(schedule.getDoctorProfile()).thenReturn(profile);
        when(schedule.getDayOfWeek()).thenReturn(monday.getDayOfWeek());
        when(schedule.getStartTime()).thenReturn(LocalTime.of(8, 30));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(10, 30));
        when(schedule.getSlotDurationMinutes()).thenReturn(60);
        when(scheduleRepository.findActiveBySpecialtyIgnoreCase("Khám Tổng Quát"))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.countBookedByDoctorsAndDateRange(
                List.of(doctorStaffId), monday, monday, AppointmentStatus.BOOKED))
                .thenReturn(List.of(new DoctorSlotBookingCount(doctorStaffId, monday, LocalTime.of(8, 30), 1L)));

        List<AvailableSlotResponse> slots = configuredService.find("Khám Tổng Quát", monday, monday);

        assertEquals(1, slots.size());
        assertEquals(LocalTime.of(9, 30), slots.get(0).startTime());
        verify(scheduleRepository).findActiveBySpecialtyIgnoreCase("Khám Tổng Quát");
        verify(appointmentRepository).countBookedByDoctorsAndDateRange(
                List.of(doctorStaffId), monday, monday, AppointmentStatus.BOOKED);
        verify(appointmentRepository, org.mockito.Mockito.never())
                .countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}

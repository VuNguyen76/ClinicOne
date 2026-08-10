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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    void keepsCheckedInAppointmentsOccupyingTheirFallbackSlot() {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        when(appointmentRepository.countBookedBySpecialtyAndDateRange(
                "Khám Tổng Quát", monday, monday, AppointmentStatus.BOOKED))
                .thenReturn(List.of());
        when(appointmentRepository.countBookedBySpecialtyAndDateRange(
                "Khám Tổng Quát", monday, monday, AppointmentStatus.CHECKED_IN))
                .thenReturn(List.of(new SlotBookingCount(monday, LocalTime.of(7, 30), 10L)));

        List<AvailableSlotResponse> slots = service.find("Khám Tổng Quát", monday, monday);

        assertEquals(6, slots.size());
        assertEquals(LocalTime.of(8, 30), slots.get(0).startTime());
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
    void usesSelectedServiceDurationWhenReturningFallbackSlots() {
        ClinicServiceRepository serviceRepository = mock(ClinicServiceRepository.class);
        ClinicService clinicService = mock(ClinicService.class);
        UUID serviceId = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(clinicService));
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(clinicService.getDurationMinutes()).thenReturn(30);
        when(appointmentRepository.countBookedBySpecialtyAndDateRange(
                "Khám Tổng Quát", monday, monday, AppointmentStatus.BOOKED)).thenReturn(List.of());

        AppointmentAvailabilityService serviceWithCatalog = new AppointmentAvailabilityService(
                appointmentRepository, new SpecialtyCatalogService(), null, null, null,
                java.time.Clock.systemUTC(), serviceRepository);

        List<AvailableSlotResponse> slots = serviceWithCatalog.find("Khám Tổng Quát", monday, monday, serviceId);

        assertEquals(14, slots.size());
        assertEquals(LocalTime.of(8, 30), slots.get(1).endTime());
    }

    @Test
    void prefersPersistedGeneratedSlotsForSelectedService() {
        ClinicServiceRepository serviceRepository = mock(ClinicServiceRepository.class);
        GeneratedClinicSlotRepository slotRepository = mock(GeneratedClinicSlotRepository.class);
        ClinicService clinicService = mock(ClinicService.class);
        GeneratedClinicSlot generatedSlot = mock(GeneratedClinicSlot.class);
        UUID serviceId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(clinicService));
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(clinicService.getDurationMinutes()).thenReturn(30);
        when(slotRepository.findByClinicServiceIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
                serviceId, monday, monday, GeneratedSlotStatus.OPEN)).thenReturn(List.of(generatedSlot));
        when(generatedSlot.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(generatedSlot.getAppointmentDate()).thenReturn(monday);
        when(generatedSlot.getStartTime()).thenReturn(LocalTime.of(8, 0));
        when(generatedSlot.getEndTime()).thenReturn(LocalTime.of(8, 30));
        when(generatedSlot.getDoctorStaffId()).thenReturn(doctorId);
        when(generatedSlot.getDoctorName()).thenReturn("Bác sĩ An");
        when(generatedSlot.getRoomCode()).thenReturn("TQ-01");
        when(appointmentRepository.countBookedByDoctorsAndDateRange(
                List.of(doctorId), monday, monday, AppointmentStatus.BOOKED)).thenReturn(List.of());

        AppointmentAvailabilityService configuredService = new AppointmentAvailabilityService(
                appointmentRepository, new SpecialtyCatalogService(), null, null, null,
                java.time.Clock.systemUTC(), serviceRepository, slotRepository);

        List<AvailableSlotResponse> slots = configuredService.find("Khám Tổng Quát", monday, monday, serviceId);

        assertEquals(1, slots.size());
        assertEquals(LocalTime.of(8, 0), slots.get(0).startTime());
        assertEquals("TQ-01", slots.get(0).roomCode());
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

    @Test
    void validatesBookableDateUsingClinicTimezone() {
        Clock justAfterClinicMidnight = Clock.fixed(Instant.parse("2026-08-10T17:30:00Z"), ZoneOffset.UTC);
        AppointmentAvailabilityService clinicClockService = new AppointmentAvailabilityService(
                appointmentRepository, new SpecialtyCatalogService(), null, null, null,
                justAfterClinicMidnight);

        AuthException exception = assertThrows(AuthException.class,
                () -> clinicClockService.ensureBookable("Khám Tổng Quát", LocalDate.of(2026, 8, 10),
                        LocalTime.of(7, 30)));

        assertEquals("APPOINTMENT_SLOT_INVALID", exception.getCode());
    }
}

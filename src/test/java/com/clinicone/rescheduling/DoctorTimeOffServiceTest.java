package com.clinicone.rescheduling;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.schedule.AppointmentHoldRepository;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorTimeOffServiceTest {
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 8, 10);
    private static final LocalDate TO = LocalDate.of(2026, 8, 11);

    private DoctorProfileRepository doctorRepository;
    private DoctorTimeOffRepository timeOffRepository;
    private GeneratedClinicSlotRepository slotRepository;
    private AppointmentRepository appointmentRepository;
    private AppointmentHoldRepository holdRepository;
    private ReschedulingService reschedulingService;
    private DoctorTimeOffService service;

    @BeforeEach
    void setUp() {
        doctorRepository = mock(DoctorProfileRepository.class);
        timeOffRepository = mock(DoctorTimeOffRepository.class);
        slotRepository = mock(GeneratedClinicSlotRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        holdRepository = mock(AppointmentHoldRepository.class);
        reschedulingService = mock(ReschedulingService.class);
        service = new DoctorTimeOffService(doctorRepository, timeOffRepository, slotRepository,
                appointmentRepository, holdRepository, reschedulingService,
                Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void locksOpenGeneratedSlotsReleasesHoldsAndCreatesRescheduleCases() {
        DoctorProfile profile = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        when(doctorRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(profile));
        when(profile.isActive()).thenReturn(true);
        when(profile.getId()).thenReturn(PROFILE_ID);
        when(profile.getStaffAccount()).thenReturn(staff);
        when(staff.getId()).thenReturn(DOCTOR_ID);
        when(timeOffRepository.save(any(DoctorTimeOff.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        GeneratedClinicSlot free = mock(GeneratedClinicSlot.class);
        GeneratedClinicSlot booked = mock(GeneratedClinicSlot.class);
        when(free.getDoctorStaffId()).thenReturn(DOCTOR_ID);
        when(free.getAppointmentDate()).thenReturn(FROM);
        when(free.getStartTime()).thenReturn(LocalTime.of(8, 0));
        when(booked.getDoctorStaffId()).thenReturn(DOCTOR_ID);
        when(booked.getAppointmentDate()).thenReturn(FROM);
        when(booked.getStartTime()).thenReturn(LocalTime.of(8, 30));
        when(slotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
                DOCTOR_ID, FROM, TO, GeneratedSlotStatus.OPEN)).thenReturn(List.of(free, booked));
        when(appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                DOCTOR_ID, FROM, LocalTime.of(8, 0), AppointmentStatus.BOOKED)).thenReturn(0L);
        when(appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                DOCTOR_ID, FROM, LocalTime.of(8, 30), AppointmentStatus.BOOKED)).thenReturn(1L);
        when(holdRepository.deleteActiveByDoctorAndDateRange(any(), any(), any(), any())).thenReturn(2);
        when(reschedulingService.openForDoctorTimeOff(DOCTOR_ID, FROM, TO, "Bác sĩ nghỉ đột xuất")).thenReturn(1);

        DoctorTimeOffResponse response = service.create(new CreateDoctorTimeOffRequest(
                DOCTOR_ID, FROM, TO, "Bác sĩ nghỉ đột xuất"));

        assertEquals(1, response.lockedSlotCount());
        assertEquals(2, response.releasedHoldCount());
        assertEquals(1, response.affectedAppointmentCount());
        verify(free).cancel();
        verify(booked, never()).cancel();
        verify(slotRepository).saveAll(List.of(free));
    }

    @Test
    void rejectsTimeOffLongerThanThirtyDaysBeforeWriting() {
        AuthException exception = assertThrows(AuthException.class, () -> service.create(new CreateDoctorTimeOffRequest(
                DOCTOR_ID, FROM, FROM.plusDays(30), "Bác sĩ nghỉ đột xuất")));

        assertEquals("DOCTOR_TIME_OFF_RANGE_INVALID", exception.getCode());
        verify(timeOffRepository, never()).save(any(DoctorTimeOff.class));
    }
}

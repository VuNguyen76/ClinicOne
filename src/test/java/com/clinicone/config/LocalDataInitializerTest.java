package com.clinicone.config;

import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import com.clinicone.schedule.CreateScheduleTemplateRequest;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import com.clinicone.schedule.ScheduleBreakRequest;
import com.clinicone.schedule.ScheduleTemplateService;
import com.clinicone.schedule.WorkScheduleTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {
    @Mock StaffAccountRepository staffRepository;
    @Mock ClinicRoomRepository roomRepository;
    @Mock DoctorProfileRepository profileRepository;
    @Mock DoctorScheduleRepository scheduleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PatientAccountRepository patientRepository;
    @Mock com.clinicone.patientprofile.PatientProfileRepository patientProfileRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock ClinicServiceRepository clinicServiceRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock WorkScheduleTemplateRepository workScheduleTemplateRepository;
    @Mock ScheduleTemplateService scheduleTemplateService;
    @Mock GeneratedClinicSlotRepository generatedClinicSlotRepository;

    @Test
    void excludesOnlyDatesWithOpenSlotsThatOverlapTheSeededSchedule() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        GeneratedClinicSlot overlapping = slot(monday, LocalTime.of(8, 30), LocalTime.of(9, 0));
        GeneratedClinicSlot outsideWorkingHours = slot(monday, LocalTime.of(17, 0), LocalTime.of(17, 30));
        GeneratedClinicSlot duringBreak = slot(monday, LocalTime.of(12, 15), LocalTime.of(12, 45));
        GeneratedClinicSlot otherWeekday = slot(monday.plusDays(1), LocalTime.of(8, 30), LocalTime.of(9, 0));
        GeneratedClinicSlot outsideRange = slot(monday.plusWeeks(1), LocalTime.of(8, 30), LocalTime.of(9, 0));

        Set<LocalDate> excludedDates = LocalDataInitializer.findConflictingScheduleDates(
                List.of(overlapping, outsideWorkingHours, duringBreak, otherWeekday, outsideRange),
                monday, monday.plusDays(4), Set.of(DayOfWeek.MONDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30,
                List.of(new ScheduleBreakRequest(LocalTime.of(12, 0), LocalTime.of(13, 0))));

        assertThat(excludedDates).containsExactly(monday);
    }

    private GeneratedClinicSlot slot(LocalDate date, LocalTime start, LocalTime end) {
        GeneratedClinicSlot slot = org.mockito.Mockito.mock(GeneratedClinicSlot.class);
        org.mockito.Mockito.lenient().when(slot.getAppointmentDate()).thenReturn(date);
        org.mockito.Mockito.lenient().when(slot.getStartTime()).thenReturn(start);
        org.mockito.Mockito.lenient().when(slot.getEndTime()).thenReturn(end);
        return slot;
    }

    @Test
    void createsLocalStaffRoomProfileAndWeekdaySchedules() {
        when(staffRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.empty());
        when(staffRepository.save(any(StaffAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(roomRepository.findAllByOrderByCodeAsc()).thenReturn(List.of());
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findByStaffAccount_Id(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(any(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findByPhone(any())).thenReturn(Optional.empty());
        when(patientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(any())).thenReturn(List.of());
        when(patientProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findByAppointmentCode(any())).thenReturn(Optional.empty());
        when(appointmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(clinicServiceRepository.save(any(ClinicService.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc()).thenReturn(List.of());
        LocalDate monday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        List<GeneratedClinicSlot> conflictingSlots = List.of(
                slot(monday, LocalTime.of(8, 30), LocalTime.of(9, 0)),
                slot(monday, LocalTime.of(13, 30), LocalTime.of(14, 0)));
        when(generatedClinicSlotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
                any(), any(), any(), any(GeneratedSlotStatus.class))).thenReturn(conflictingSlots);
        when(generatedClinicSlotRepository.findByRoomIdAndAppointmentDateBetweenAndStatus(
                any(), any(), any(), any(GeneratedSlotStatus.class))).thenReturn(List.of());

        LocalDataInitializer initializer = new LocalDataInitializer(staffRepository, roomRepository, profileRepository, scheduleRepository,
                passwordEncoder, patientRepository, patientProfileRepository, appointmentRepository,
                clinicServiceRepository, jdbcTemplate,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password");
        ReflectionTestUtils.setField(initializer, "workScheduleTemplateRepository", workScheduleTemplateRepository);
        ReflectionTestUtils.setField(initializer, "scheduleTemplateService", scheduleTemplateService);
        ReflectionTestUtils.setField(initializer, "generatedClinicSlotRepository", generatedClinicSlotRepository);
        initializer.run();

        verify(staffRepository, org.mockito.Mockito.times(10)).save(any(StaffAccount.class));
        verify(roomRepository, org.mockito.Mockito.times(10)).save(any());
        verify(profileRepository, org.mockito.Mockito.times(7)).save(any());
        verify(scheduleRepository, org.mockito.Mockito.times(35)).save(any());
        ArgumentCaptor<ClinicService> serviceCaptor = ArgumentCaptor.forClass(ClinicService.class);
        verify(clinicServiceRepository, org.mockito.Mockito.times(7)).save(serviceCaptor.capture());
        assertThat(serviceCaptor.getAllValues()).hasSize(7);
        ArgumentCaptor<CreateScheduleTemplateRequest> templateCaptor =
                ArgumentCaptor.forClass(CreateScheduleTemplateRequest.class);
        verify(scheduleTemplateService, org.mockito.Mockito.times(7)).create(templateCaptor.capture());
        assertThat(templateCaptor.getAllValues()).allSatisfy(request -> assertThat(request.exceptionDates()).contains(monday));
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, org.mockito.Mockito.times(14)).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getAllValues()).contains(
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password");
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(passwordEncoder);
    }
}

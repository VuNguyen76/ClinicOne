package com.clinicone.config;

import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import com.clinicone.schedule.CreateScheduleTemplateRequest;
import com.clinicone.schedule.ScheduleTemplateService;
import com.clinicone.schedule.WorkScheduleTemplateRepository;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.queue.QueuePresenceStatus;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketStatus;
import com.clinicone.queue.QueueClosureOutcome;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.rescheduling.DoctorTimeOffRepository;
import com.clinicone.rescheduling.RescheduleCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Mock WorkScheduleTemplateRepository workScheduleTemplateRepository;
    @Mock ScheduleTemplateService scheduleTemplateService;
    @Mock DoctorTimeOffRepository doctorTimeOffRepository;
    @Mock RescheduleCaseRepository rescheduleCaseRepository;
    @Mock QueueTicketRepository queueTicketRepository;
    @Mock ExaminationSessionRepository examinationSessionRepository;

    @Test
    void skipsAllFixtureWritesWhenInitializerIsNotExplicitlyEnabled() {
        LocalDataInitializer initializer = new LocalDataInitializer(staffRepository, roomRepository, profileRepository,
                scheduleRepository, passwordEncoder, patientRepository, patientProfileRepository,
                appointmentRepository, clinicServiceRepository,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password",
                false, "jdbc:postgresql://pg-chuyensaigon-prod-10930.postgres.database.azure.com:5432/clinicone");

        initializer.run();

        verifyNoInteractions(staffRepository, roomRepository, profileRepository, scheduleRepository, passwordEncoder,
                patientRepository, patientProfileRepository, appointmentRepository, clinicServiceRepository);
    }

    @Test
    void refusesFixtureWritesWhenExplicitlyEnabledAgainstRemoteDatabase() {
        LocalDataInitializer initializer = new LocalDataInitializer(staffRepository, roomRepository, profileRepository,
                scheduleRepository, passwordEncoder, patientRepository, patientProfileRepository,
                appointmentRepository, clinicServiceRepository,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password",
                true, "jdbc:postgresql://pg-chuyensaigon-prod-10930.postgres.database.azure.com:5432/clinicone");

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-local database");
        verifyNoInteractions(staffRepository, roomRepository, profileRepository, scheduleRepository, passwordEncoder,
                patientRepository, patientProfileRepository, appointmentRepository, clinicServiceRepository);
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

        LocalDataInitializer initializer = new LocalDataInitializer(staffRepository, roomRepository, profileRepository, scheduleRepository,
                passwordEncoder, patientRepository, patientProfileRepository, appointmentRepository,
                clinicServiceRepository,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password",
                true, "jdbc:postgresql://localhost:5432/clinicone-test");
        ReflectionTestUtils.setField(initializer, "workScheduleTemplateRepository", workScheduleTemplateRepository);
        ReflectionTestUtils.setField(initializer, "scheduleTemplateService", scheduleTemplateService);
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
        verify(scheduleTemplateService, org.mockito.Mockito.times(7)).createForLocalBootstrap(templateCaptor.capture());
        assertThat(templateCaptor.getAllValues()).allSatisfy(request -> assertThat(request.exceptionDates()).isEmpty());
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, org.mockito.Mockito.times(14)).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getAllValues()).contains(
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password");
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void seedsDateScopedExamAndRescheduleFlowsWithServiceSnapshotsForTheNextThreeDays() {
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
        when(patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(patientProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findByAppointmentCode(any())).thenReturn(Optional.empty());
        when(appointmentRepository.findByPatientProfileId(any())).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(clinicServiceRepository.save(any(ClinicService.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc()).thenReturn(List.of());
        when(doctorTimeOffRepository.existsActiveOnDate(any(), any())).thenReturn(false);
        when(doctorTimeOffRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rescheduleCaseRepository.findByAppointmentIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(rescheduleCaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationSessionRepository.findByAppointment_Id(any())).thenReturn(Optional.empty());
        when(examinationSessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(queueTicketRepository.findByAppointmentId(any())).thenReturn(Optional.empty());

        LocalDataInitializer initializer = new LocalDataInitializer(staffRepository, roomRepository, profileRepository,
                scheduleRepository, passwordEncoder, patientRepository, patientProfileRepository,
                appointmentRepository, clinicServiceRepository,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password",
                true, "jdbc:postgresql://localhost:5432/clinicone-test");
        ReflectionTestUtils.setField(initializer, "workScheduleTemplateRepository", workScheduleTemplateRepository);
        ReflectionTestUtils.setField(initializer, "scheduleTemplateService", scheduleTemplateService);
        ReflectionTestUtils.setField(initializer, "doctorTimeOffRepository", doctorTimeOffRepository);
        ReflectionTestUtils.setField(initializer, "rescheduleCaseRepository", rescheduleCaseRepository);
        ReflectionTestUtils.setField(initializer, "queueTicketRepository", queueTicketRepository);
        ReflectionTestUtils.setField(initializer, "examinationSessionRepository", examinationSessionRepository);

        initializer.run();

        ArgumentCaptor<Appointment> appointments = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, org.mockito.Mockito.atLeastOnce()).save(appointments.capture());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        List<LocalDate> clinicDates = new ArrayList<>();
        for (LocalDate date = today; clinicDates.size() < 4; date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                clinicDates.add(date);
            }
        }
        List<LocalDate> seededDates = List.of(today, clinicDates.get(1), clinicDates.get(2));
        List<Appointment> demoAppointments = appointments.getAllValues().stream()
                .filter(item -> item.getAppointmentCode().startsWith("DMO-"))
                .toList();
        assertThat(demoAppointments).isNotEmpty().allSatisfy(item -> {
            assertThat(seededDates).contains(item.getAppointmentDate());
            assertThat(item.getServiceName()).isNotBlank();
            assertThat(item.getServiceDurationMinutes()).isEqualTo(30);
        });
        assertThat(demoAppointments).extracting(Appointment::getAppointmentDate)
                .containsAll(seededDates);
        ArgumentCaptor<QueueTicket> tickets = ArgumentCaptor.forClass(QueueTicket.class);
        verify(queueTicketRepository, org.mockito.Mockito.atLeastOnce()).save(tickets.capture());
        assertThat(tickets.getAllValues()).extracting(QueueTicket::getStatus)
                .contains(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED, QueueTicketStatus.IN_SERVICE,
                        QueueTicketStatus.LEFT_BEFORE_EXAM, QueueTicketStatus.COMPLETED);
        assertThat(tickets.getAllValues()).anySatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(QueueTicketStatus.WAITING);
            assertThat(ticket.getPresenceStatus()).isEqualTo(QueuePresenceStatus.RETURN_REQUIRED);
            assertThat(ticket.getSkipReason()).isNotBlank();
        });
        assertThat(tickets.getAllValues()).anySatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(QueueTicketStatus.CALLED);
            assertThat(ticket.getCallCount()).isEqualTo(2);
            assertThat(ticket.getPresenceStatus()).isEqualTo(QueuePresenceStatus.READY);
        });
        assertThat(tickets.getAllValues()).anySatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(QueueTicketStatus.WAITING);
            assertThat(ticket.isPriority()).isTrue();
        });
        assertThat(tickets.getAllValues()).anySatisfy(ticket ->
                assertThat(ticket.getClosureOutcome()).isEqualTo(QueueClosureOutcome.FACILITY_UNAVAILABLE));
        assertThat(tickets.getAllValues()).anySatisfy(ticket ->
                assertThat(ticket.getClosureOutcome()).isEqualTo(QueueClosureOutcome.LEFT_BEFORE_EXAM));
        assertThat(tickets.getAllValues()).anySatisfy(ticket ->
                assertThat(ticket.getClosureOutcome()).isEqualTo(QueueClosureOutcome.EXAMINATION_STOPPED));
        assertThat(appointments.getAllValues()).extracting(Appointment::getStatus)
                .contains(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED,
                        AppointmentStatus.NOT_PERFORMED, AppointmentStatus.ABSENT, AppointmentStatus.CANCELLED);
        verify(rescheduleCaseRepository, org.mockito.Mockito.times(3)).save(any());
        verify(doctorTimeOffRepository, org.mockito.Mockito.times(3)).save(any());
    }
}

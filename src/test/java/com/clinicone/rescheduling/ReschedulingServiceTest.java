package com.clinicone.rescheduling;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.schedule.AppointmentAvailabilityService;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.DayOfWeek;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReschedulingServiceTest {
    private static final UUID DOCTOR_ID = UUID.fromString("c5e4f7d2-9d7a-4bcb-95c7-1d9a8c1e31d5");
    private static final UUID APPOINTMENT_ID = UUID.fromString("c6b7d8e9-f0a1-4234-8567-9a0b1c2d3e4f");
    private static final UUID PATIENT_ID = UUID.fromString("d7e8f9a0-b1c2-43d4-95e6-7f8a9b0c1d2e");
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final RescheduleCaseRepository caseRepository = mock(RescheduleCaseRepository.class);
    private final AppointmentAvailabilityService availabilityService = mock(AppointmentAvailabilityService.class);
    private final PatientNotificationService notificationService = mock(PatientNotificationService.class);
    private final GeneratedClinicSlotRepository generatedSlotRepository = mock(GeneratedClinicSlotRepository.class);
    private ReschedulingService service;

    @BeforeEach
    void setUp() {
        service = new ReschedulingService(appointmentRepository, caseRepository, availabilityService,
                notificationService, Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC),
                null, generatedSlotRepository);
        when(caseRepository.findByAppointmentIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(caseRepository.save(any(RescheduleCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void opensOneCaseAndLeavesBookedAppointmentUntouchedWhenScheduleIsRemoved() throws Exception {
        Appointment appointment = appointment();
        DoctorSchedule schedule = schedule();
        when(appointmentRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
                DOCTOR_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9), AppointmentStatus.BOOKED))
                .thenReturn(List.of(appointment));

        int opened = service.openForScheduleRemoval(schedule);

        assertEquals(1, opened);
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        verify(caseRepository).save(any(RescheduleCase.class));
    }

    @Test
    void doesNotDuplicateAnAlreadyOpenCase() throws Exception {
        Appointment appointment = appointment();
        DoctorSchedule schedule = schedule();
        when(appointmentRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
                DOCTOR_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9), AppointmentStatus.BOOKED))
                .thenReturn(List.of(appointment));
        when(caseRepository.findByAppointmentIdAndStatus(APPOINTMENT_ID, RescheduleCaseStatus.OPEN))
                .thenReturn(Optional.of(RescheduleCase.open(appointment, "existing")));

        assertEquals(0, service.openForScheduleRemoval(schedule));
        verify(caseRepository, never()).save(any(RescheduleCase.class));
    }

    @Test
    void resolvesCaseAfterCoordinatorChoosesAvailableReplacement() throws Exception {
        Appointment appointment = appointment();
        appointment.applyServiceSnapshot(UUID.randomUUID(), "KhÃ¡m tá»•ng quÃ¡t", "KhÃ¡m", 30, true);
        GeneratedClinicSlot oldSlot = mock(GeneratedClinicSlot.class);
        when(generatedSlotRepository.findFirstByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                DOCTOR_ID, appointment.getAppointmentDate(), appointment.getStartTime(), GeneratedSlotStatus.OPEN))
                .thenReturn(Optional.of(oldSlot));
        RescheduleCase rescheduleCase = RescheduleCase.open(appointment, "Bác sĩ nghỉ");
        UUID caseId = UUID.randomUUID();
        setId(rescheduleCase, caseId);
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(rescheduleCase));
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(caseRepository.save(any(RescheduleCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RescheduleCaseResponse response = service.resolve(caseId, new ResolveRescheduleRequest(
                LocalDate.of(2026, 8, 11), LocalTime.of(9, 30), "Bác sĩ An", DOCTOR_ID), "coordinator");

        assertEquals(RescheduleCaseStatus.RESOLVED, response.status());
        assertEquals(LocalDate.of(2026, 8, 11), appointment.getAppointmentDate());
        assertEquals(LocalTime.of(9, 30), appointment.getStartTime());
        verify(oldSlot).cancel();
        verify(generatedSlotRepository).save(oldSlot);
        verify(notificationService).notifyAppointmentRescheduled(appointment, "2026-08-10", "08:30");
    }

    @Test
    void rejectsResolvingAClosedCase() throws Exception {
        Appointment appointment = appointment();
        RescheduleCase closed = RescheduleCase.open(appointment, "Bác sĩ nghỉ");
        closed.resolve(LocalDate.of(2026, 8, 11), LocalTime.of(9, 30), DOCTOR_ID, "Bác sĩ An", Instant.now());
        UUID caseId = UUID.randomUUID();
        setId(closed, caseId);
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(closed));

        assertThrows(RuntimeException.class, () -> service.resolve(caseId, new ResolveRescheduleRequest(
                LocalDate.of(2026, 8, 12), LocalTime.of(9, 30), "Bác sĩ An", DOCTOR_ID), "coordinator"));
    }

    @Test
    void patientCanReadOwnOpenCaseAndReplacementSlots() throws Exception {
        Appointment appointment = appointment();
        RescheduleCase item = openCase(appointment);
        when(caseRepository.findByAppointmentIdAndStatus(APPOINTMENT_ID, RescheduleCaseStatus.OPEN))
                .thenReturn(Optional.of(item));
        when(availabilityService.find("Nội tổng quát", LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 20))).thenReturn(List.of(
                new com.clinicone.schedule.AvailableSlotResponse("Nội tổng quát", LocalDate.of(2026, 8, 11),
                        LocalTime.of(9, 30), LocalTime.of(10, 0), "Bác sĩ An", 1, DOCTOR_ID, "NOI-01")));

        RescheduleCaseResponse response = service.findForPatient(PATIENT_ID.toString(), APPOINTMENT_ID);
        List<com.clinicone.schedule.AvailableSlotResponse> slots = service.alternativesForPatient(
                PATIENT_ID.toString(), APPOINTMENT_ID, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 20));

        assertEquals(RescheduleCaseStatus.OPEN, response.status());
        assertEquals(APPOINTMENT_ID, response.appointmentId());
        assertEquals(1, slots.size());
        verify(availabilityService).find("Nội tổng quát", LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 20));
    }

    @Test
    void patientCannotReadAnotherPatientsCase() throws Exception {
        Appointment appointment = appointment();
        RescheduleCase item = openCase(appointment);
        when(caseRepository.findByAppointmentIdAndStatus(APPOINTMENT_ID, RescheduleCaseStatus.OPEN))
                .thenReturn(Optional.of(item));

        assertThrows(RuntimeException.class, () -> service.findForPatient(
                UUID.randomUUID().toString(), APPOINTMENT_ID));
        verifyNoInteractions(availabilityService);
    }

    @Test
    void patientCanConfirmOwnReplacementAndResolveCase() throws Exception {
        Appointment appointment = appointment();
        RescheduleCase item = openCase(appointment);
        UUID caseId = item.getId();
        when(caseRepository.findByAppointmentIdAndStatus(APPOINTMENT_ID, RescheduleCaseStatus.OPEN))
                .thenReturn(Optional.of(item));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(item));
        when(caseRepository.save(any(RescheduleCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RescheduleCaseResponse response = service.resolveForPatient(PATIENT_ID.toString(), APPOINTMENT_ID,
                new ResolveRescheduleRequest(LocalDate.of(2026, 8, 11), LocalTime.of(9, 30),
                        "Bác sĩ An", DOCTOR_ID));

        assertEquals(RescheduleCaseStatus.RESOLVED, response.status());
        assertEquals(LocalDate.of(2026, 8, 11), appointment.getAppointmentDate());
        assertEquals(LocalTime.of(9, 30), appointment.getStartTime());
        verify(notificationService).notifyAppointmentRescheduled(appointment, "2026-08-10", "08:30");
    }

    private Appointment appointment() throws Exception {
        PatientAccount patient = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        Appointment appointment = Appointment.create(patient, DOCTOR_ID, "CL-20260810-AB12", "Nội tổng quát",
                "Bác sĩ An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        setId(appointment, APPOINTMENT_ID);
        setId(patient, PATIENT_ID);
        return appointment;
    }

    private RescheduleCase openCase(Appointment appointment) throws Exception {
        RescheduleCase item = RescheduleCase.open(appointment, "Bác sĩ nghỉ");
        setId(item, UUID.randomUUID());
        return item;
    }

    private DoctorSchedule schedule() throws Exception {
        StaffAccount staff = StaffAccount.create("bs.an", "hash", "Bác sĩ An", StaffRole.DOCTOR);
        setId(staff, DOCTOR_ID);
        DoctorProfile profile = DoctorProfile.create(staff, "Nội tổng quát", null);
        setId(profile, UUID.randomUUID());
        return DoctorSchedule.create(profile, DayOfWeek.MONDAY, LocalTime.of(8, 30), LocalTime.of(10, 30), 60);
    }

    private static void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}

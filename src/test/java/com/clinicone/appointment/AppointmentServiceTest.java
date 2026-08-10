package com.clinicone.appointment;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.schedule.AppointmentAvailabilityService;
import com.clinicone.schedule.AppointmentHold;
import com.clinicone.schedule.AppointmentHoldService;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.auth.StaffAccount;
import com.clinicone.config.ClinicConfigurationService;
import com.clinicone.reason.ReasonCatalog;
import com.clinicone.reason.ReasonCatalogService;
import com.clinicone.reason.ReasonCatalogType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;

class AppointmentServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private PatientAccountRepository accountRepository;
    private AppointmentRepository appointmentRepository;
    private PatientNotificationService notificationService;
    private AppointmentAvailabilityService availabilityService;
    private AppointmentHoldService holdService;
    private com.clinicone.schedule.ClinicServiceRepository clinicServiceRepository;
    private ReasonCatalogService reasonCatalogService;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        notificationService = mock(PatientNotificationService.class);
        availabilityService = mock(AppointmentAvailabilityService.class);
        holdService = mock(AppointmentHoldService.class);
        clinicServiceRepository = mock(com.clinicone.schedule.ClinicServiceRepository.class);
        reasonCatalogService = mock(ReasonCatalogService.class);
        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAppointmentForAuthenticatedPatient() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài"));

        assertEquals("Nội khoa", response.specialty());
        assertEquals("BS. Nguyễn An", response.doctorName());
        assertEquals("Đã đặt", response.statusLabel());
        verify(appointmentRepository).save(any(Appointment.class));
        verify(notificationService).notifyAppointmentCreated(any(Appointment.class));
    }

    @Test
    void persistsSelectedClinicServiceSnapshotOnAppointment() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID serviceId = UUID.randomUUID();
        com.clinicone.schedule.ClinicService clinicService = mock(com.clinicone.schedule.ClinicService.class);
        when(clinicService.getId()).thenReturn(serviceId);
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getName()).thenReturn("Khám tổng quát cơ bản");
        when(clinicService.getSpecialty()).thenReturn("Nội khoa");
        when(clinicService.getVisitType()).thenReturn("Khám thường");
        when(clinicService.getDurationMinutes()).thenReturn(30);
        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.of(clinicService));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài", null, null, null, serviceId));

        assertEquals(serviceId, response.serviceId());
        assertEquals("Khám tổng quát cơ bản", response.serviceName());
        assertEquals("Khám thường", response.visitType());
        assertEquals(30, response.serviceDurationMinutes());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void rejectsUnknownClinicServiceBeforeCreatingAppointment() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID serviceId = UUID.randomUUID();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài", null, null, null, serviceId)));

        assertEquals("CLINIC_SERVICE_NOT_FOUND", exception.getCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void rejectsDoctorOutsideSelectedClinicService() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID serviceId = UUID.randomUUID();
        UUID requestedDoctorId = UUID.randomUUID();
        StaffAccount eligibleStaff = mock(StaffAccount.class);
        when(eligibleStaff.getId()).thenReturn(UUID.randomUUID());
        DoctorProfile eligibleDoctor = mock(DoctorProfile.class);
        when(eligibleDoctor.getStaffAccount()).thenReturn(eligibleStaff);
        com.clinicone.schedule.ClinicService clinicService = mock(com.clinicone.schedule.ClinicService.class);
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getSpecialty()).thenReturn("Nội khoa");
        when(clinicService.getEligibleDoctors()).thenReturn(Set.of(eligibleDoctor));
        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.of(clinicService));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài", null, requestedDoctorId, null, serviceId)));

        assertEquals("CLINIC_SERVICE_DOCTOR_NOT_ELIGIBLE", exception.getCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void consumesOwnHoldWhenAppointmentIsCreated() throws Exception {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID holdId = UUID.randomUUID();
        AppointmentHold hold = AppointmentHold.create(account, "Nội khoa", "BS. Nguyễn An", null,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "PATIENT-HOLD", java.time.Instant.MAX);
        setId(hold, holdId);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED))
                .thenReturn(Optional.empty());
        when(holdService.requireForBooking(eq(ACCOUNT_ID.toString()), eq(holdId), any())).thenReturn(hold);

        service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest("Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu", null, null, holdId));

        verify(availabilityService).ensureBookable("Nội khoa", "BS. Nguyễn An", null,
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), holdId);
        verify(holdService).consume(hold);
    }

    @Test
    void rejectsDuplicateAppointmentForSamePatientAndTime() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED))
                .thenReturn(Optional.of(Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu")));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu")));

        assertEquals(409, exception.getStatus().value());
        assertEquals("APPOINTMENT_DUPLICATE", exception.getCode());
    }

    @Test
    void listsOnlyAppointmentsBelongingToAuthenticatedPatient() {
        when(appointmentRepository.findByPatientIdOrderByAppointmentDateAscStartTimeAsc(ACCOUNT_ID))
                .thenReturn(List.of());

        assertEquals(List.of(), service.list(ACCOUNT_ID.toString()));
        verify(appointmentRepository).findByPatientIdOrderByAppointmentDateAscStartTimeAsc(ACCOUNT_ID);
    }

    @Test
    void returnsAppointmentDetailOnlyForItsPatient() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        setId(appointment, appointmentId);
        when(appointmentRepository.findByIdAndPatientId(appointmentId, ACCOUNT_ID)).thenReturn(Optional.of(appointment));

        assertEquals(appointmentId, service.get(ACCOUNT_ID.toString(), appointmentId.toString()).id());
    }

    @Test
    void cancelsBookedAppointment() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));

        service.cancel(ACCOUNT_ID.toString(), UUID.randomUUID().toString(), new CancelAppointmentRequest("Bận việc"));

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        verify(notificationService).notifyAppointmentCancelled(appointment);
    }

    @Test
    void requiresReasonWhenCancellingWithinConfiguredThreshold() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(13, 0), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        ClinicConfigurationService configuration = mock(ClinicConfigurationService.class);
        when(configuration.current()).thenReturn(com.clinicone.config.ClinicConfiguration.defaults());
        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository, configuration,
                Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC));

        AuthException exception = assertThrows(AuthException.class, () -> service.cancel(
                ACCOUNT_ID.toString(), UUID.randomUUID().toString(), new CancelAppointmentRequest("   ")));

        assertEquals("CANCELLATION_REASON_REQUIRED", exception.getCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void storesSelectedCancellationCatalogLabelInsteadOfFreeText() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 20), LocalTime.of(13, 0), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        ReasonCatalog reason = ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "SCHEDULE_CHANGE", "Thay đổi kế hoạch");
        when(reasonCatalogService.requireActive(ReasonCatalogType.APPOINTMENT_CANCELLATION, "SCHEDULE_CHANGE"))
                .thenReturn(reason);
        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository, null, reasonCatalogService,
                Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC));

        service.cancel(ACCOUNT_ID.toString(), UUID.randomUUID().toString(),
                new CancelAppointmentRequest("nội dung không được tin cậy", "SCHEDULE_CHANGE"));

        assertEquals("Thay đổi kế hoạch", appointment.getCancellationReason());
    }

    @Test
    void reschedulesBookedAppointmentWhenNewSlotIsFree() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        AppointmentResponse response = service.reschedule(ACCOUNT_ID.toString(), UUID.randomUUID().toString(),
                new RescheduleAppointmentRequest(LocalDate.of(2026, 8, 11), LocalTime.of(10, 0)));

        assertEquals(LocalDate.of(2026, 8, 11), response.appointmentDate());
        assertEquals(LocalTime.of(10, 0), response.startTime());
        verify(notificationService).notifyAppointmentRescheduled(appointment, "2026-08-10", "08:30");
    }

    @Test
    void keepsSelectedClinicServiceWhenRescheduling() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID serviceId = UUID.randomUUID();
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        appointment.applyServiceSnapshot(serviceId, "Khám tổng quát", "Khám thường", 30);
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        service.reschedule(ACCOUNT_ID.toString(), UUID.randomUUID().toString(),
                new RescheduleAppointmentRequest(LocalDate.of(2026, 8, 11), LocalTime.of(10, 0)));

        verify(availabilityService).ensureBookable("Nội khoa", "BS. Nguyễn An", null,
                LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), null, serviceId);
    }

    private static void setId(PatientAccount account, UUID id) {
        try {
            var field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setId(Appointment appointment, UUID id) {
        try {
            var field = Appointment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(appointment, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setId(com.clinicone.schedule.AppointmentHold hold, UUID id) {
        try {
            var field = com.clinicone.schedule.AppointmentHold.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(hold, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

package com.clinicone.appointment;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.schedule.AppointmentAvailabilityService;
import com.clinicone.schedule.AppointmentHold;
import com.clinicone.schedule.AppointmentHoldService;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.auth.StaffAccount;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.config.ClinicConfigurationService;
import com.clinicone.reason.ReasonCatalog;
import com.clinicone.reason.ReasonCatalogService;
import com.clinicone.reason.ReasonCatalogType;
import com.clinicone.rescheduling.RescheduleCase;
import com.clinicone.rescheduling.RescheduleCaseRepository;
import com.clinicone.rescheduling.RescheduleCaseStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class AppointmentServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private PatientAccountRepository accountRepository;
    private AppointmentRepository appointmentRepository;
    private PatientNotificationService notificationService;
    private AppointmentAvailabilityService availabilityService;
    private AppointmentHoldService holdService;
    private com.clinicone.schedule.ClinicServiceRepository clinicServiceRepository;
    private ReasonCatalogService reasonCatalogService;
    private RescheduleCaseRepository rescheduleCaseRepository;
    private com.clinicone.schedule.GeneratedClinicSlotRepository generatedSlotRepository;
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
        rescheduleCaseRepository = mock(RescheduleCaseRepository.class);
        generatedSlotRepository = mock(GeneratedClinicSlotRepository.class);
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
    void createsAppointmentForTemporaryReceptionProfileWithoutPatientNotification() {
        PatientProfile temporaryProfile = PatientProfile.createTemporary(
                "Nguyen Van Tam", LocalDate.of(1990, 1, 1), "Nam", "0912345678", null, null, null, null);
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài");

        AppointmentResponse response = service.createTemporary(temporaryProfile, request);

        assertEquals("Nội khoa", response.specialty());
        assertEquals("Đã đặt", response.statusLabel());
        verify(appointmentRepository).save(any(Appointment.class));
        verify(notificationService, never()).notifyAppointmentCreated(any(Appointment.class));
    }

    @Test
    void repeatedCreateWithSameIdempotencyKeyReturnsTheExistingAppointment() {
        Appointment existing = Appointment.existing(
                new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false),
                "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10),
                LocalTime.of(8, 30), "Đau đầu kéo dài");
        UUID existingId = UUID.randomUUID();
        setId(existing, existingId);
        when(appointmentRepository.findByPatientIdAndCreationRequestKey(ACCOUNT_ID, "booking-key-1"))
                .thenReturn(Optional.of(existing));

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài"), "booking-key-1");

        assertEquals(existingId, response.id());
        verify(accountRepository, never()).findById(ACCOUNT_ID);
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(notificationService, never()).notifyAppointmentCreated(any(Appointment.class));
    }

    @Test
    void rejectsReusingCreateKeyForDifferentPayload() {
        Appointment existing = Appointment.existing(
                new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false),
                "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10),
                LocalTime.of(8, 30), "Đau đầu kéo dài");
        when(appointmentRepository.findByPatientIdAndCreationRequestKey(ACCOUNT_ID, "booking-key-2"))
                .thenReturn(Optional.of(existing));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentRequest("Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10),
                        LocalTime.of(9, 0), "Đau đầu kéo dài"), "booking-key-2"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void generatesOpaqueAppointmentCodeWithoutDateOrPatientData() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 11), LocalTime.of(8, 30), AppointmentStatus.BOOKED))
                .thenReturn(Optional.empty());
        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository, null, reasonCatalogService,
                Clock.fixed(Instant.parse("2026-08-10T17:30:00Z"), ZoneOffset.UTC));

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 11), LocalTime.of(8, 30),
                "Đau đầu kéo dài"));

        assertTrue(response.appointmentCode().matches("CL-[A-Z0-9]{12}"));
        assertFalse(response.appointmentCode().contains("20260811"));
        assertFalse(response.appointmentCode().contains("0912345678"));
    }

    @Test
    void retriesAppointmentCodeGenerationWhenCandidateAlreadyExists() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        AppointmentCodeGenerator codeGenerator = mock(AppointmentCodeGenerator.class);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(codeGenerator.nextCode()).thenReturn("CL-ALREADYUSED1", "CL-NEWCODE98765");
        when(appointmentRepository.findByAppointmentCode("CL-ALREADYUSED1"))
                .thenReturn(Optional.of(Appointment.existing(account, "CL-ALREADYUSED1", "Nội khoa", "BS. Nguyễn An",
                        LocalDate.of(2026, 8, 9), LocalTime.of(8, 30), "Đau đầu")));
        when(appointmentRepository.findByAppointmentCode("CL-NEWCODE98765")).thenReturn(Optional.empty());

        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository, null, reasonCatalogService,
                Clock.systemUTC(), codeGenerator);

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 11), LocalTime.of(8, 30),
                "Đau đầu kéo dài"));

        assertEquals("CL-NEWCODE98765", response.appointmentCode());
        verify(appointmentRepository).findByAppointmentCode("CL-ALREADYUSED1");
        verify(appointmentRepository).findByAppointmentCode("CL-NEWCODE98765");
    }

    @Test
    void rejectsBookingWithoutChangingDataWhenAllCodeRetriesCollide() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        AppointmentCodeGenerator codeGenerator = mock(AppointmentCodeGenerator.class);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(codeGenerator.nextCode()).thenReturn("CL-COLLISION01");
        when(appointmentRepository.findByAppointmentCode("CL-COLLISION01"))
                .thenReturn(Optional.of(Appointment.existing(account, "CL-COLLISION01", "Nội khoa", "BS. Nguyễn An",
                        LocalDate.of(2026, 8, 9), LocalTime.of(8, 30), "Đau đầu")));
        service = new AppointmentService(accountRepository, appointmentRepository, null, availabilityService,
                notificationService, null, holdService, clinicServiceRepository, null, reasonCatalogService,
                Clock.systemUTC(), codeGenerator);

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentRequest("Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 11),
                        LocalTime.of(8, 30), "Đau đầu kéo dài")));

        assertEquals("APPOINTMENT_CODE_UNAVAILABLE", exception.getCode());
        verify(appointmentRepository, times(5)).findByAppointmentCode("CL-COLLISION01");
        verify(appointmentRepository, never()).save(any(Appointment.class));
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
    void rejectsDuplicateAppointmentWhenExistingAppointmentWasCheckedIn() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment checkedIn = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        checkedIn.checkIn();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED))
                .thenReturn(Optional.empty());
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.CHECKED_IN))
                .thenReturn(Optional.of(checkedIn));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(),
                new CreateAppointmentRequest("Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10),
                        LocalTime.of(8, 30), "Đau đầu")));

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
    void retriesCancellationWithSameKeyWithoutRepeatingSideEffects() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        UUID appointmentId = UUID.randomUUID();
        setId(appointment, appointmentId);
        when(appointmentRepository.findByIdAndPatientId(appointmentId, ACCOUNT_ID)).thenReturn(Optional.of(appointment));

        service.cancel(ACCOUNT_ID.toString(), appointmentId.toString(), new CancelAppointmentRequest("Bận việc"),
                "cancel-request-1");
        service.cancel(ACCOUNT_ID.toString(), appointmentId.toString(), new CancelAppointmentRequest("Bận việc"),
                "cancel-request-1");

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals("cancel-request-1", appointment.getCancellationRequestKey());
        verify(appointmentRepository).save(appointment);
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
    void reschedulingAConfiguredAppointmentClosesItsPreviousGeneratedSlot() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID doctorId = UUID.randomUUID();
        Appointment appointment = Appointment.create(account, doctorId, "CL-20260810-AB12", "Ná»™i khoa",
                "BS. Nguyá»…n An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Äau Ä‘áº§u");
        UUID serviceId = UUID.randomUUID();
        appointment.applyServiceSnapshot(serviceId, "KhÃ¡m tá»•ng quÃ¡t", "KhÃ¡m", 30, true);
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), AppointmentStatus.BOOKED))
                .thenReturn(Optional.empty());
        GeneratedClinicSlot oldSlot = mock(GeneratedClinicSlot.class);
        when(generatedSlotRepository.findFirstByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                doctorId, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), GeneratedSlotStatus.OPEN))
                .thenReturn(Optional.of(oldSlot));
        AppointmentService configuredService = new AppointmentService(accountRepository, appointmentRepository, null,
                availabilityService, notificationService, null, holdService, clinicServiceRepository, null,
                reasonCatalogService, Clock.systemUTC(), new AppointmentCodeGenerator(), rescheduleCaseRepository,
                generatedSlotRepository);

        configuredService.reschedule(ACCOUNT_ID.toString(), UUID.randomUUID().toString(),
                new RescheduleAppointmentRequest(LocalDate.of(2026, 8, 11), LocalTime.of(10, 0)));

        verify(oldSlot).cancel();
        verify(generatedSlotRepository).save(oldSlot);
    }

    @Test
    void rejectsLegacyRescheduleWhenAnOpenReschedulingCaseExists() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        UUID appointmentId = UUID.randomUUID();
        setId(appointment, appointmentId);
        when(appointmentRepository.findByIdAndPatientId(appointmentId, ACCOUNT_ID)).thenReturn(Optional.of(appointment));
        when(rescheduleCaseRepository.findByAppointmentIdAndStatus(appointmentId, RescheduleCaseStatus.OPEN))
                .thenReturn(Optional.of(RescheduleCase.open(appointment, "Bác sĩ nghỉ")));

        AppointmentService guardedService = new AppointmentService(accountRepository, appointmentRepository, null,
                availabilityService, notificationService, null, holdService, clinicServiceRepository, null,
                reasonCatalogService, Clock.systemUTC(), new AppointmentCodeGenerator(), rescheduleCaseRepository);

        AuthException exception = assertThrows(AuthException.class, () -> guardedService.reschedule(
                ACCOUNT_ID.toString(), appointmentId.toString(),
                new RescheduleAppointmentRequest(LocalDate.of(2026, 8, 11), LocalTime.of(10, 0))));

        assertEquals("RESCHEDULE_CASE_PENDING", exception.getCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(availabilityService, never()).ensureBookable(any(), any(), any(), any(), any());
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

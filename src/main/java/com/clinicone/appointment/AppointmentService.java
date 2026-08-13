package com.clinicone.appointment;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.schedule.AppointmentAvailabilityService;
import com.clinicone.schedule.AppointmentHold;
import com.clinicone.schedule.AppointmentHoldService;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import com.clinicone.config.ClinicConfigurationService;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.reason.ReasonCatalog;
import com.clinicone.reason.ReasonCatalogService;
import com.clinicone.reason.ReasonCatalogType;
import com.clinicone.rescheduling.RescheduleCaseRepository;
import com.clinicone.rescheduling.RescheduleCaseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AppointmentService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int APPOINTMENT_CODE_MAX_ATTEMPTS = 5;
    private final PatientAccountRepository accountRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientProfileRepository profileRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final PatientNotificationService notificationService;
    private final BusinessLogService businessLogService;
    private final AppointmentHoldService holdService;
    private final ClinicServiceRepository clinicServiceRepository;
    private final ClinicConfigurationService configurationService;
    private final ReasonCatalogService reasonCatalogService;
    private final Clock clock;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final RescheduleCaseRepository rescheduleCaseRepository;
    private final GeneratedClinicSlotRepository generatedSlotRepository;

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository) {
        this(accountRepository, appointmentRepository, null, null, null, null, null, null, null, null, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository) {
        this(accountRepository, appointmentRepository, profileRepository, null, null, null, null, null, null, null, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, null, null, null, null, null, null, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService, null, null, null, null, null, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, null, null, null, null, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, null, null, null, Clock.systemUTC());
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, clinicServiceRepository, null, null, Clock.systemUTC());
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, ReasonCatalogService reasonCatalogService,
                              Clock clock) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, clinicServiceRepository, configurationService, reasonCatalogService,
                clock, new AppointmentCodeGenerator());
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, ReasonCatalogService reasonCatalogService,
                              Clock clock, AppointmentCodeGenerator appointmentCodeGenerator) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, clinicServiceRepository, configurationService, reasonCatalogService,
                clock, appointmentCodeGenerator, null);
    }

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, ReasonCatalogService reasonCatalogService,
                              Clock clock, AppointmentCodeGenerator appointmentCodeGenerator,
                              RescheduleCaseRepository rescheduleCaseRepository) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, clinicServiceRepository, configurationService, reasonCatalogService,
                clock, appointmentCodeGenerator, rescheduleCaseRepository, null);
    }

    @Autowired
    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, ReasonCatalogService reasonCatalogService,
                              Clock clock, AppointmentCodeGenerator appointmentCodeGenerator,
                              RescheduleCaseRepository rescheduleCaseRepository,
                              GeneratedClinicSlotRepository generatedSlotRepository) {
        this.accountRepository = accountRepository;
        this.appointmentRepository = appointmentRepository;
        this.profileRepository = profileRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.businessLogService = businessLogService;
        this.holdService = holdService;
        this.clinicServiceRepository = clinicServiceRepository;
        this.configurationService = configurationService;
        this.reasonCatalogService = reasonCatalogService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.appointmentCodeGenerator = appointmentCodeGenerator == null
                ? new AppointmentCodeGenerator() : appointmentCodeGenerator;
        this.rescheduleCaseRepository = rescheduleCaseRepository;
        this.generatedSlotRepository = generatedSlotRepository;
    }

    /** Backward-compatible constructor for isolated unit tests and integrations. */
    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, Clock clock) {
        this(accountRepository, appointmentRepository, profileRepository, availabilityService, notificationService,
                businessLogService, holdService, clinicServiceRepository, configurationService, null, clock);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return appointmentRepository.findByPatientIdOrderByAppointmentDateAscStartTimeAsc(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(String accountId, String appointmentId) {
        UUID patientId = parseAccountId(accountId);
        UUID id = parseAppointmentId(appointmentId);
        return AppointmentResponse.from(findOwned(id, patientId));
    }

    @Transactional
    public AppointmentResponse create(String accountId, CreateAppointmentRequest request) {
        return create(accountId, request, null);
    }

    @Transactional
    public AppointmentResponse create(String accountId, CreateAppointmentRequest request, String requestKey) {
        UUID patientId = parseAccountId(accountId);
        String normalizedRequestKey = normalizeRequestKey(requestKey);
        if (normalizedRequestKey != null) {
            var existing = appointmentRepository.findByPatientIdAndCreationRequestKey(patientId, normalizedRequestKey);
            if (existing.isPresent()) {
                if (!sameCreateRequest(existing.get(), request)) {
                    throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                            "Khóa chống trùng đã được dùng cho một yêu cầu đặt lịch khác.");
                }
                return AppointmentResponse.from(existing.get());
            }
        }
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authenticationRequired());
        if (patient.isMustChangePassword()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_ACTIVATION_REQUIRED",
                    "Tài khoản chưa được kích hoạt. Vui lòng kích hoạt tài khoản trước khi đặt lịch.");
        }
        ClinicService selectedService = resolveService(request);
        LocalDate appointmentDate = request.appointmentDate();
        LocalTime startTime = request.startTime();
        AppointmentHold hold = null;
        if (request.holdId() != null) {
            if (holdService == null) {
                throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_HOLD_UNAVAILABLE",
                        "Không thể xác nhận giữ chỗ lúc này. Vui lòng chọn lại khung giờ.");
            }
            hold = holdService.requireForBooking(accountId, request.holdId(), request);
        }
        if (availabilityService != null) {
            if (hold == null) {
                if (request.serviceId() == null) {
                    availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                            appointmentDate, startTime);
                } else {
                    availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                            appointmentDate, startTime, null, request.serviceId());
                }
            } else {
                if (request.serviceId() == null) {
                    availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                            appointmentDate, startTime, hold.getId());
                } else {
                    availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                            appointmentDate, startTime, hold.getId(), request.serviceId());
                }
            }
        }
        if (hasActiveAppointment(patientId, appointmentDate, startTime)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
        }

        PatientProfile profile = resolveProfile(request.profileId(), patientId);
        Appointment appointment = profile == null
                ? Appointment.create(patient, request.doctorId(), nextAppointmentCode(), request.specialty().trim(),
                request.doctorName().trim(), appointmentDate, startTime, request.reason().trim())
                : Appointment.create(patient, request.doctorId(), profile, nextAppointmentCode(),
                request.specialty().trim(), request.doctorName().trim(), appointmentDate, startTime,
                request.reason().trim());
        if (selectedService != null) {
            appointment.applyServiceSnapshot(selectedService.getId(), selectedService.getName(),
                    selectedService.getVisitType(), selectedService.getDurationMinutes(),
                    selectedService.requiresMedicalRecord());
        }
        appointment.assignCreationRequestKey(normalizedRequestKey);
        Appointment saved = appointmentRepository.save(appointment);
        if (hold != null) {
            holdService.consume(hold);
        }
        recordTransition(UUID.randomUUID(), saved.getId(), null, saved.getStatus().name(), "CREATE_APPOINTMENT", accountId,
                null);
        if (notificationService != null) {
            notificationService.notifyAppointmentCreated(saved);
        }
        return AppointmentResponse.from(saved);
    }

    @Transactional
    public AppointmentResponse createTemporary(PatientProfile temporaryProfile, CreateAppointmentRequest request) {
        if (temporaryProfile == null || !temporaryProfile.isTemporaryProfile()) {
            throw new AuthException(HttpStatus.CONFLICT, "TEMPORARY_PROFILE_REQUIRED",
                    "Lịch ngoại lệ phải gắn với hồ sơ tạm tại quầy.");
        }
        if (request.profileId() != null && temporaryProfile.getId() != null
                && !temporaryProfile.getId().equals(request.profileId())) {
            throw new AuthException(HttpStatus.CONFLICT, "TEMPORARY_PROFILE_MISMATCH",
                    "Hồ sơ tạm không khớp với lịch đang tạo.");
        }
        ClinicService selectedService = resolveService(request);
        if (availabilityService != null) {
            if (request.serviceId() == null) {
                availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                        request.appointmentDate(), request.startTime());
            } else {
                availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                        request.appointmentDate(), request.startTime(), null, request.serviceId());
            }
        }
        if (temporaryProfile.getId() != null
                && appointmentRepository.existsByPatientProfile_IdAndAppointmentDateAndStartTimeAndStatusIn(
                temporaryProfile.getId(), request.appointmentDate(), request.startTime(),
                List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN))) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Hồ sơ tạm đã có lịch trong khung giờ này.");
        }
        Appointment appointment = Appointment.createTemporary(temporaryProfile, request.doctorId(),
                nextAppointmentCode(), request.specialty().trim(), request.doctorName().trim(),
                request.appointmentDate(), request.startTime(), request.reason().trim());
        if (selectedService != null) {
            appointment.applyServiceSnapshot(selectedService.getId(), selectedService.getName(),
                    selectedService.getVisitType(), selectedService.getDurationMinutes(),
                    selectedService.requiresMedicalRecord());
        }
        Appointment saved = appointmentRepository.save(appointment);
        recordTransition(UUID.randomUUID(), saved.getId(), null, saved.getStatus().name(),
                "CREATE_TEMPORARY_APPOINTMENT", "STAFF", null);
        return AppointmentResponse.from(saved);
    }

    private boolean sameCreateRequest(Appointment existing, CreateAppointmentRequest request) {
        return existing.getSpecialty().equalsIgnoreCase(request.specialty().trim())
                && existing.getDoctorName().equalsIgnoreCase(request.doctorName().trim())
                && existing.getAppointmentDate().equals(request.appointmentDate())
                && existing.getStartTime().equals(request.startTime())
                && existing.getReason().equals(request.reason().trim())
                && Objects.equals(existing.getDoctorStaffId(), request.doctorId())
                && Objects.equals(existing.getServiceId(), request.serviceId())
                && Objects.equals(existing.getPatientProfile() == null ? null : existing.getPatientProfile().getId(),
                        request.profileId());
    }

    private ClinicService resolveService(CreateAppointmentRequest request) {
        if (request.serviceId() == null) {
            return null;
        }
        if (clinicServiceRepository == null) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_UNAVAILABLE",
                    "Danh mục dịch vụ khám chưa sẵn sàng.");
        }
        ClinicService service = clinicServiceRepository.findById(request.serviceId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CLINIC_SERVICE_NOT_FOUND",
                        "Không tìm thấy dịch vụ khám đã chọn."));
        if (!service.isActive()) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_INACTIVE",
                    "Dịch vụ khám đã tạm ngưng nhận lịch.");
        }
        if (!service.getSpecialty().equalsIgnoreCase(request.specialty().trim())) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_SPECIALTY_MISMATCH",
                    "Dịch vụ không thuộc chuyên khoa đã chọn.");
        }
        var eligibleDoctors = service.getEligibleDoctors();
        if (eligibleDoctors != null && !eligibleDoctors.isEmpty()) {
            if (request.doctorId() == null || eligibleDoctors.stream()
                    .map(doctor -> doctor.getStaffAccount().getId())
                    .noneMatch(request.doctorId()::equals)) {
                throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_DOCTOR_NOT_ELIGIBLE",
                        "Bác sĩ đã chọn không thực hiện dịch vụ này.");
            }
        }
        return service;
    }

    private PatientProfile resolveProfile(UUID profileId, UUID patientId) {
        if (profileId == null || profileRepository == null) {
            return null;
        }
        return profileRepository.findByIdAndOwnerIdAndActiveTrue(profileId, patientId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PATIENT_PROFILE_NOT_FOUND",
                        "Không tìm thấy hồ sơ được chọn."));
    }

    @Transactional
    public void cancel(String accountId, String appointmentId, CancelAppointmentRequest request) {
        cancel(accountId, appointmentId, request, null);
    }

    @Transactional
    public void cancel(String accountId, String appointmentId, CancelAppointmentRequest request,
                       String requestKey) {
        Appointment appointment = findOwned(parseAppointmentId(appointmentId), parseAccountId(accountId));
        String normalizedRequestKey = normalizeRequestKey(requestKey);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                && normalizedRequestKey != null
                && normalizedRequestKey.equals(appointment.getCancellationRequestKey())) {
            return;
        }
        ensureBookable(appointment);
        String cancellationReason = resolveCancellationReason(request);
        requireLateCancellationReason(appointment, cancellationReason);
        String previousStatus = appointment.getStatus().name();
        UUID eventId = UUID.randomUUID();
        appointment.cancel(cancellationReason, Instant.now(clock), normalizedRequestKey);
        // A past slot can no longer be offered again. Future cancellations leave
        // the generated slot open so another patient may book it.
        markCancelledSlotUnavailableIfPast(appointment);
        appointmentRepository.save(appointment);
        recordTransition(eventId, appointment.getId(), previousStatus, appointment.getStatus().name(), "CANCEL_APPOINTMENT",
                accountId, cancellationReason);
        if (notificationService != null) {
            notificationService.notifyAppointmentCancelled(appointment);
        }
    }

    @Transactional
    public AppointmentResponse reschedule(String accountId, String appointmentId, RescheduleAppointmentRequest request) {
        UUID patientId = parseAccountId(accountId);
        Appointment appointment = findOwned(parseAppointmentId(appointmentId), patientId);
        ensureBookable(appointment);
        if (rescheduleCaseRepository != null
                && rescheduleCaseRepository.findByAppointmentIdAndStatus(appointment.getId(), RescheduleCaseStatus.OPEN)
                .isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "RESCHEDULE_CASE_PENDING",
                    "Lịch hẹn đang chờ xác nhận khung giờ thay thế.");
        }
        boolean sameSlot = appointment.getAppointmentDate().equals(request.appointmentDate())
                && appointment.getStartTime().equals(request.startTime());
        if (availabilityService != null && !sameSlot) {
            if (appointment.getServiceId() == null) {
                availabilityService.ensureBookable(appointment.getSpecialty(), appointment.getDoctorName(),
                        appointment.getDoctorStaffId(), request.appointmentDate(), request.startTime());
            } else {
                availabilityService.ensureBookable(appointment.getSpecialty(), appointment.getDoctorName(),
                        appointment.getDoctorStaffId(), request.appointmentDate(), request.startTime(), null,
                        appointment.getServiceId());
            }
        }
        if (!sameSlot && hasActiveAppointment(patientId, request.appointmentDate(), request.startTime())) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
        }
        if (!sameSlot) {
            markPreviousGeneratedSlotUnavailable(appointment);
        }
        String previousDate = appointment.getAppointmentDate().toString();
        String previousTime = appointment.getStartTime().toString();
        appointment.reschedule(request.appointmentDate(), request.startTime());
        Appointment saved = appointmentRepository.save(appointment);
        if (notificationService != null && !sameSlot) {
            notificationService.notifyAppointmentRescheduled(saved, previousDate, previousTime);
        }
        return AppointmentResponse.from(saved);
    }

    /**
     * Moves a booked appointment after the patient arrives late. The original
     * appointment and code are retained; only the booked slot and assigned
     * doctor are changed. This is deliberately separate from the patient
     * self-service reschedule flow so that reception can record the reason and
     * use the same late-window rules as the lifecycle job.
     */
    @Transactional
    public AppointmentResponse rescheduleForReception(UUID appointmentId, UUID doctorStaffId, String doctorName,
                                                      LocalDate appointmentDate, LocalTime startTime,
                                                      String reason, String actor) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(this::appointmentNotFound);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AuthException(HttpStatus.CONFLICT, "LATE_RESCHEDULE_STATUS_INVALID",
                    "Chỉ lịch hẹn đang đặt mới được chuyển sang khung giờ khác.");
        }
        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason == null || normalizedReason.length() < 3 || normalizedReason.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "LATE_RESCHEDULE_REASON_INVALID",
                    "Lý do đến muộn phải có từ 3 đến 500 ký tự.");
        }
        if (!appointment.getAppointmentDate().equals(today())
                || !isWithinLateRescheduleWindow(appointment)) {
            throw new AuthException(HttpStatus.CONFLICT, "LATE_RESCHEDULE_WINDOW_CLOSED",
                    "Chỉ có thể chuyển lịch trong thời gian đến muộn của ngày khám.");
        }
        boolean sameSlot = appointment.getAppointmentDate().equals(appointmentDate)
                && appointment.getStartTime().equals(startTime)
                && Objects.equals(appointment.getDoctorStaffId(), doctorStaffId);
        if (sameSlot) {
            throw new AuthException(HttpStatus.CONFLICT, "LATE_RESCHEDULE_SLOT_UNCHANGED",
                    "Vui lòng chọn một khung giờ hoặc bác sĩ khác.");
        }
        if (availabilityService == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "APPOINTMENT_AVAILABILITY_UNAVAILABLE",
                    "Chưa thể kiểm tra khung giờ thay thế.");
        }
        availabilityService.ensureBookable(appointment.getSpecialty(), doctorName, doctorStaffId,
                appointmentDate, startTime, null, appointment.getServiceId());
        if (appointment.getPatient() != null
                && hasActiveAppointment(appointment.getPatient().getId(), appointmentDate, startTime)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Người bệnh đã có lịch hẹn trong khung giờ này.");
        }

        String previousDate = appointment.getAppointmentDate().toString();
        String previousTime = appointment.getStartTime().toString();
        markPreviousGeneratedSlotUnavailable(appointment);
        appointment.reschedule(appointmentDate, startTime, doctorStaffId, doctorName);
        Appointment saved = appointmentRepository.save(appointment);
        if (businessLogService != null) {
            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", saved.getId(),
                    AppointmentStatus.BOOKED.name(), AppointmentStatus.BOOKED.name(),
                    "RECEPTION_RESCHEDULE_LATE", actor, normalizedReason);
        }
        if (notificationService != null) {
            notificationService.notifyAppointmentRescheduled(saved, previousDate, previousTime);
        }
        return AppointmentResponse.from(saved);
    }

    private boolean isWithinLateRescheduleWindow(Appointment appointment) {
        Instant scheduledEnd = ZonedDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime(), CLINIC_ZONE)
                .toInstant()
                .plusSeconds(effectiveDurationMinutes(appointment) * 60L);
        Instant now = Instant.now(clock);
        return !now.isBefore(scheduledEnd.plus(Duration.ofMinutes(15)))
                && now.isBefore(scheduledEnd.plus(Duration.ofHours(24)));
    }

    private int effectiveDurationMinutes(Appointment appointment) {
        Integer duration = appointment.getServiceDurationMinutes();
        return duration == null || duration <= 0 ? 60 : duration;
    }

    private LocalDate today() {
        return ZonedDateTime.now(clock).withZoneSameInstant(CLINIC_ZONE).toLocalDate();
    }

    private void markPreviousGeneratedSlotUnavailable(Appointment appointment) {
        if (generatedSlotRepository == null || appointment.getServiceId() == null
                || appointment.getDoctorStaffId() == null) {
            return;
        }
        generatedSlotRepository
                .findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getServiceId(), appointment.getDoctorStaffId(), appointment.getAppointmentDate(),
                        appointment.getStartTime(), GeneratedSlotStatus.OPEN)
                .ifPresent(slot -> {
                    slot.cancel();
                    generatedSlotRepository.save(slot);
                });
    }

    private void markCancelledSlotUnavailableIfPast(Appointment appointment) {
        if (generatedSlotRepository == null || appointment.getServiceId() == null
                || appointment.getDoctorStaffId() == null) {
            return;
        }
        Instant appointmentAt = ZonedDateTime.of(appointment.getAppointmentDate(),
                appointment.getStartTime(), CLINIC_ZONE).toInstant();
        if (Instant.now(clock).isBefore(appointmentAt)) {
            return;
        }
        generatedSlotRepository
                .findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getServiceId(), appointment.getDoctorStaffId(), appointment.getAppointmentDate(),
                        appointment.getStartTime(), GeneratedSlotStatus.OPEN)
                .ifPresent(slot -> {
                    slot.cancel();
                    generatedSlotRepository.save(slot);
                });
    }

    private String nextAppointmentCode() {
        for (int attempt = 0; attempt < APPOINTMENT_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = appointmentCodeGenerator.nextCode();
            if (appointmentRepository.findByAppointmentCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "APPOINTMENT_CODE_UNAVAILABLE",
                "Không thể tạo mã lịch hẹn duy nhất lúc này. Vui lòng thử lại.");
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw authenticationRequired();
        }
    }

    private UUID parseAppointmentId(String appointmentId) {
        try {
            return UUID.fromString(appointmentId);
        } catch (IllegalArgumentException exception) {
            throw appointmentNotFound();
        }
    }

    private Appointment findOwned(UUID appointmentId, UUID patientId) {
        return appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(this::appointmentNotFound);
    }

    private void ensureBookable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_ACTIONABLE",
                    "Lịch hẹn này không còn cho phép thao tác.");
        }
    }

    private AuthException appointmentNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.");
    }

    private AuthException authenticationRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Phiên đăng nhập không hợp lệ.");
    }

    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 80) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Khóa chống trùng không được dài quá 80 ký tự.");
        }
        return normalized;
    }

    private boolean hasActiveAppointment(UUID patientId, LocalDate appointmentDate, LocalTime startTime) {
        return appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                        patientId, appointmentDate, startTime, AppointmentStatus.BOOKED).isPresent()
                || appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                        patientId, appointmentDate, startTime, AppointmentStatus.CHECKED_IN).isPresent();
    }

    private void requireLateCancellationReason(Appointment appointment, String reason) {
        Instant appointmentAt = ZonedDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime(), CLINIC_ZONE).toInstant();
        Duration remaining = Duration.between(Instant.now(clock), appointmentAt);
        int thresholdHours = configurationService == null
                ? 12
                : configurationService.current().getCancellationThresholdHours();
        if (!remaining.isNegative() && remaining.compareTo(Duration.ofHours(thresholdHours)) <= 0
                && (reason == null || reason.isBlank())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CANCELLATION_REASON_REQUIRED",
                    "Cần chọn lý do khi hủy lịch trong thời gian quy định.");
        }
    }

    private String resolveCancellationReason(CancelAppointmentRequest request) {
        if (request == null) {
            return null;
        }
        if (reasonCatalogService == null) {
            return request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        }
        if ((request.reasonCode() == null || request.reasonCode().isBlank())
                && (request.reason() == null || request.reason().isBlank())) {
            return null;
        }
        if (request.reasonCode() == null || request.reasonCode().isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CANCELLATION_REASON_REQUIRED",
                    "Hãy chọn một lý do trong danh mục.");
        }
        ReasonCatalog reason = reasonCatalogService.requireActive(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                request.reasonCode());
        return reason.getLabel();
    }

    private void recordTransition(UUID eventId, UUID appointmentId, String previousStatus, String nextStatus,
                                  String eventType, String actor, String reason) {
        if (businessLogService != null && appointmentId != null) {
            businessLogService.recordTransition(eventId, "APPOINTMENT", appointmentId, previousStatus, nextStatus,
                    eventType, actor, reason);
        }
    }
}

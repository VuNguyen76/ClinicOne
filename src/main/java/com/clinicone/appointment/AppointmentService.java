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
import com.clinicone.config.ClinicConfigurationService;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.reason.ReasonCatalog;
import com.clinicone.reason.ReasonCatalogService;
import com.clinicone.reason.ReasonCatalogType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AppointmentService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository, AppointmentAvailabilityService availabilityService,
                              PatientNotificationService notificationService, BusinessLogService businessLogService,
                              AppointmentHoldService holdService, ClinicServiceRepository clinicServiceRepository,
                              ClinicConfigurationService configurationService, ReasonCatalogService reasonCatalogService,
                              Clock clock) {
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
        UUID patientId = parseAccountId(accountId);
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authenticationRequired());
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
        if (appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                patientId, appointmentDate, startTime, AppointmentStatus.BOOKED).isPresent()) {
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
        Appointment appointment = findOwned(parseAppointmentId(appointmentId), parseAccountId(accountId));
        ensureBookable(appointment);
        String cancellationReason = resolveCancellationReason(request);
        requireLateCancellationReason(appointment, cancellationReason);
        String previousStatus = appointment.getStatus().name();
        UUID eventId = UUID.randomUUID();
        appointment.cancel(cancellationReason, Instant.now(clock));
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
        if (!sameSlot && appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                patientId, request.appointmentDate(), request.startTime(), AppointmentStatus.BOOKED).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
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

    private String nextAppointmentCode() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "CL-" + LocalDate.now().toString().replace("-", "") + "-" + suffix;
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

    private void requireLateCancellationReason(Appointment appointment, String reason) {
        Instant appointmentAt = java.time.ZonedDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime(), CLINIC_ZONE).toInstant();
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

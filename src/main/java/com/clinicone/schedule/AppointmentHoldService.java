package com.clinicone.schedule;

import com.clinicone.auth.AuthenticatedIds;
import com.clinicone.appointment.CreateAppointmentRequest;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.config.ClinicConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Service
public class AppointmentHoldService {
    private static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

    private final PatientAccountRepository accountRepository;
    private final AppointmentHoldRepository holdRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final Clock clock;
    private final ClinicServiceRepository clinicServiceRepository;
    private final ClinicConfigurationService configurationService;

    public AppointmentHoldService(PatientAccountRepository accountRepository,
                                  AppointmentHoldRepository holdRepository,
                                  AppointmentAvailabilityService availabilityService,
                                  Clock clock) {
        this(accountRepository, holdRepository, availabilityService, clock, null);
    }

    public AppointmentHoldService(PatientAccountRepository accountRepository,
                                  AppointmentHoldRepository holdRepository,
                                  AppointmentAvailabilityService availabilityService,
                                  Clock clock,
                                  ClinicServiceRepository clinicServiceRepository) {
        this(accountRepository, holdRepository, availabilityService, clock, clinicServiceRepository, null);
    }

    @Autowired
    public AppointmentHoldService(PatientAccountRepository accountRepository,
                                  AppointmentHoldRepository holdRepository,
                                  AppointmentAvailabilityService availabilityService,
                                  Clock clock,
                                  ClinicServiceRepository clinicServiceRepository,
                                  ClinicConfigurationService configurationService) {
        this.accountRepository = accountRepository;
        this.holdRepository = holdRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
        this.clinicServiceRepository = clinicServiceRepository;
        this.configurationService = configurationService;
    }

    @Transactional
    public AppointmentHoldResponse create(String accountId, CreateAppointmentHoldRequest request) {
        return create(accountId, request, null);
    }

    @Transactional
    public AppointmentHoldResponse create(String accountId, CreateAppointmentHoldRequest request, String sessionKey) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authRequired());
        validateService(request);
        Instant now = Instant.now(clock);
        String holdKey = holdKey(patientId, request);
        var existing = holdRepository.findByHoldKey(holdKey);
        if (existing.isPresent()) {
            AppointmentHold hold = existing.get();
            if (hold.getExpiresAt().isAfter(now)) {
                if (!hold.getPatient().getId().equals(patientId)) {
                    throw slotHeld();
                }
                return AppointmentHoldResponse.from(hold);
            }
            holdRepository.delete(hold);
            holdRepository.flush();
        }

        // Một tài khoản chỉ giữ một khung giờ tại một thời điểm. Khi người bệnh
        // chọn khung mới, giải phóng khung cũ ngay trong cùng giao dịch để không
        // khóa lịch của người khác quá lâu.
        boolean legacySession = sessionKey == null || sessionKey.isBlank();
        String normalizedSessionKey = normalizeSessionKey(sessionKey);
        var activeHolds = legacySession
                ? holdRepository.findByPatientIdAndExpiresAtAfter(patientId, now)
                : holdRepository.findByPatientIdAndSessionKeyAndExpiresAtAfter(patientId, normalizedSessionKey, now);
        activeHolds.stream()
                .filter(hold -> !hold.getHoldKey().equals(holdKey))
                .forEach(holdRepository::delete);

        if (request.serviceId() == null) {
            availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                    request.appointmentDate(), request.startTime());
        } else {
            availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                    request.appointmentDate(), request.startTime(), null, request.serviceId());
        }

        AppointmentHold hold = AppointmentHold.create(patient, request.specialty().trim(), request.doctorName().trim(),
                request.doctorId(), request.appointmentDate(), request.startTime(), holdKey,
                now.plus(holdDuration()), request.serviceId(), normalizedSessionKey);
        try {
            return AppointmentHoldResponse.from(holdRepository.saveAndFlush(hold));
        } catch (DataIntegrityViolationException exception) {
            throw slotHeld();
        }
    }

    @Transactional
    public AppointmentHold requireForBooking(String accountId, UUID holdId, CreateAppointmentRequest request) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        AppointmentHold hold = holdRepository.findByIdAndPatientId(holdId, patientId)
                .orElseThrow(() -> holdMissing());
        Instant now = Instant.now(clock);
        if (!hold.getExpiresAt().isAfter(now)) {
            throwExpired(hold);
        }
        if (!sameSlot(hold, request)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_HOLD_MISMATCH",
                    "Khung giờ giữ chỗ không khớp với lịch đang đặt.");
        }
        return hold;
    }

    @Transactional
    public void consume(AppointmentHold hold) {
        holdRepository.delete(hold);
    }

    @Transactional
    public int releaseExpired() {
        var expired = holdRepository.findByExpiresAtLessThanEqual(Instant.now(clock));
        holdRepository.deleteAll(expired);
        return expired.size();
    }

    private boolean sameSlot(AppointmentHold hold, CreateAppointmentRequest request) {
        return hold.getSpecialty().equalsIgnoreCase(request.specialty())
                && (hold.getServiceId() == null ? request.serviceId() == null
                : hold.getServiceId().equals(request.serviceId()))
                && (hold.getDoctorStaffId() == null ? request.doctorId() == null
                : hold.getDoctorStaffId().equals(request.doctorId()))
                && hold.getAppointmentDate().equals(request.appointmentDate())
                && hold.getStartTime().equals(request.startTime());
    }

    private String holdKey(UUID patientId, CreateAppointmentHoldRequest request) {
        String date = request.appointmentDate().toString();
        String time = request.startTime().toString();
        String profilePart = request.profileId() != null ? ":PROFILE:" + request.profileId() : "";
        if (request.doctorId() != null) {
            if (request.serviceId() == null) {
                return "DOCTOR:" + request.doctorId() + ":" + date + ":" + time + profilePart;
            }
            return "DOCTOR:" + request.doctorId() + ":" + request.serviceId() + ":" + date + ":" + time + profilePart;
        }
        String base = "PATIENT:" + patientId + ":" + request.specialty().trim().toLowerCase();
        String key = request.serviceId() == null
                ? base + ":" + date + ":" + time
                : base + ":" + request.serviceId() + ":" + date + ":" + time;
        return key + profilePart;
    }

    private void validateService(CreateAppointmentHoldRequest request) {
        if (request.serviceId() == null || clinicServiceRepository == null) {
            return;
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
        if (eligibleDoctors != null && !eligibleDoctors.isEmpty()
                && (request.doctorId() == null || eligibleDoctors.stream()
                .map(doctor -> doctor.getStaffAccount().getId())
                .noneMatch(request.doctorId()::equals))) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_DOCTOR_NOT_ELIGIBLE",
                    "Bác sĩ đã chọn không thực hiện dịch vụ này.");
        }
    }

    private Duration holdDuration() {
        if (configurationService == null) return DEFAULT_HOLD_DURATION;
        return Duration.ofMinutes(configurationService.current().getHoldMinutes());
    }

    private void throwExpired(AppointmentHold hold) {
        holdRepository.delete(hold);
        throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_HOLD_EXPIRED",
                "Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ.");
    }

    private AuthException authRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Phiên đăng nhập không hợp lệ.");
    }

    private AuthException holdMissing() {
        return new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_HOLD_NOT_FOUND",
                "Khung giờ không còn được giữ. Vui lòng chọn lại.");
    }

    private AuthException slotHeld() {
        return new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_HELD",
                "Khung giờ vừa được người khác giữ. Vui lòng chọn khung giờ khác.");
    }

    private String normalizeSessionKey(String sessionKey) {
        String normalized = sessionKey == null ? "" : sessionKey.trim();
        if (normalized.isEmpty()) return "LEGACY";
        if (normalized.length() > 120) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SESSION_KEY_INVALID",
                    "Mã phiên đăng nhập không hợp lệ.");
        }
        return normalized;
    }
}

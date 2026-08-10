package com.clinicone.schedule;

import com.clinicone.appointment.CreateAppointmentRequest;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
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
    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final PatientAccountRepository accountRepository;
    private final AppointmentHoldRepository holdRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final Clock clock;

    public AppointmentHoldService(PatientAccountRepository accountRepository,
                                  AppointmentHoldRepository holdRepository,
                                  AppointmentAvailabilityService availabilityService,
                                  Clock clock) {
        this.accountRepository = accountRepository;
        this.holdRepository = holdRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    @Transactional
    public AppointmentHoldResponse create(String accountId, CreateAppointmentHoldRequest request) {
        UUID patientId = parseAccountId(accountId);
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authRequired());
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

        availabilityService.ensureBookable(request.specialty(), request.doctorName(), request.doctorId(),
                request.appointmentDate(), request.startTime());

        AppointmentHold hold = AppointmentHold.create(patient, request.specialty().trim(), request.doctorName().trim(),
                request.doctorId(), request.appointmentDate(), request.startTime(), holdKey,
                now.plus(HOLD_DURATION));
        try {
            return AppointmentHoldResponse.from(holdRepository.saveAndFlush(hold));
        } catch (DataIntegrityViolationException exception) {
            throw slotHeld();
        }
    }

    @Transactional
    public AppointmentHold requireForBooking(String accountId, UUID holdId, CreateAppointmentRequest request) {
        UUID patientId = parseAccountId(accountId);
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
                && (hold.getDoctorStaffId() == null ? request.doctorId() == null
                : hold.getDoctorStaffId().equals(request.doctorId()))
                && hold.getAppointmentDate().equals(request.appointmentDate())
                && hold.getStartTime().equals(request.startTime());
    }

    private String holdKey(UUID patientId, CreateAppointmentHoldRequest request) {
        String date = request.appointmentDate().toString();
        String time = request.startTime().toString();
        if (request.doctorId() != null) {
            return "DOCTOR:" + request.doctorId() + ":" + date + ":" + time;
        }
        return "PATIENT:" + patientId + ":" + request.specialty().trim().toLowerCase() + ":" + date + ":" + time;
    }

    private void throwExpired(AppointmentHold hold) {
        holdRepository.delete(hold);
        throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_HOLD_EXPIRED",
                "Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ.");
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw authRequired();
        }
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
}

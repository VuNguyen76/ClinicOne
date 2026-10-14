package com.clinicone.rescheduling;

import lombok.RequiredArgsConstructor;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.schedule.AppointmentHoldRepository;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import com.clinicone.schedule.GeneratedSlotCancellationReason;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DoctorTimeOffService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_DAYS = 30;

    private final DoctorProfileRepository doctorRepository;
    private final DoctorTimeOffRepository timeOffRepository;
    private final GeneratedClinicSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHoldRepository holdRepository;
    private final ReschedulingService reschedulingService;
    private final Clock clock;

    @Transactional
    public DoctorTimeOffResponse create(CreateDoctorTimeOffRequest request) {
        validate(request);
        // Share the same doctor-row lock as appointment booking so a new leave
        // cannot race with a booking that has already passed availability checks.
        DoctorProfile doctor = doctorRepository.findByStaffAccount_IdForUpdate(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_AVAILABLE",
                        "Bác sĩ không tồn tại hoặc đang ngừng hoạt động."));

        if (timeOffRepository.existsOverlapping(doctor.getId(), request.startDate(), request.endDate())) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_TIME_OFF_OVERLAP",
                    "Bác sĩ đã có lịch nghỉ trùng với khoảng thời gian này.");
        }

        DoctorTimeOff timeOff = timeOffRepository.save(DoctorTimeOff.create(doctor, request.startDate(),
                request.endDate(), request.reason()));
        List<GeneratedClinicSlot> openSlots = slotRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(request.doctorId(), request.startDate(),
                        request.endDate(), GeneratedSlotStatus.OPEN);
        List<GeneratedClinicSlot> lockedSlots = openSlots.stream()
                .filter(slot -> appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        request.doctorId(), slot.getAppointmentDate(), slot.getStartTime(), AppointmentStatus.BOOKED) == 0)
                .toList();
        lockedSlots.forEach(GeneratedClinicSlot::cancelForDoctorTimeOff);
        if (!lockedSlots.isEmpty()) {
            slotRepository.saveAll(lockedSlots);
        }

        Instant now = Instant.now(clock.withZone(CLINIC_ZONE));
        int releasedHolds = holdRepository.deleteActiveByDoctorAndDateRange(request.doctorId(), request.startDate(),
                request.endDate(), now);
        int affectedAppointments = reschedulingService.openForDoctorTimeOff(request.doctorId(), request.startDate(),
                request.endDate(), request.reason().trim());
        return DoctorTimeOffResponse.from(timeOff, lockedSlots.size(), releasedHolds, affectedAppointments);
    }

    @Transactional
    public DoctorTimeOffResponse update(UUID id, CreateDoctorTimeOffRequest request) {
        DoctorTimeOff current = findEditable(id);
        validate(request);
        if (!current.isActive() || !current.getDoctorProfile().getStaffAccount().getId().equals(request.doctorId())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_TIME_OFF_IDENTITY_IMMUTABLE",
                    "Không thể đổi bác sĩ của lịch nghỉ đã ghi nhận.");
        }
        if (current.getStartDate().isBefore(LocalDate.now(clock.withZone(CLINIC_ZONE)))) {
            throw conflict("DOCTOR_TIME_OFF_ALREADY_STARTED", "Không thể sửa lịch nghỉ đã bắt đầu.");
        }
        DoctorProfile doctor = lockDoctor(request.doctorId());
        ensureNoActiveAppointments(current);
        if (timeOffRepository.existsOverlappingExcept(doctor.getId(), request.startDate(), request.endDate(), id)) {
            throw conflict("DOCTOR_TIME_OFF_OVERLAP", "Bác sĩ đã có lịch nghỉ trùng với khoảng thời gian này.");
        }
        current.setActive(false);
        timeOffRepository.save(current);
        reopenSlots(current);
        return create(request);
    }

    @Transactional
    public DoctorTimeOffResponse cancel(UUID id) {
        DoctorTimeOff current = findEditable(id);
        if (!current.isActive()) return DoctorTimeOffResponse.from(current, 0, 0, 0);
        if (current.getStartDate().isBefore(LocalDate.now(clock.withZone(CLINIC_ZONE)))) {
            throw conflict("DOCTOR_TIME_OFF_ALREADY_STARTED", "Không thể hủy lịch nghỉ đã bắt đầu.");
        }
        ensureNoActiveAppointments(current);
        current.setActive(false);
        timeOffRepository.save(current);
        reopenSlots(current);
        return DoctorTimeOffResponse.from(current, 0, 0, 0);
    }

    private DoctorTimeOff findEditable(UUID id) {
        return timeOffRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_TIME_OFF_NOT_FOUND",
                        "Không tìm thấy lịch nghỉ."));
    }

    @Transactional(readOnly = true)
    public UUID doctorStaffId(UUID id) {
        return timeOffRepository.findById(id)
                .map(item -> item.getDoctorProfile().getStaffAccount().getId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_TIME_OFF_NOT_FOUND",
                        "Không tìm thấy lịch nghỉ."));
    }

    private DoctorProfile lockDoctor(UUID staffId) {
        return doctorRepository.findByStaffAccount_IdForUpdate(staffId)
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_AVAILABLE",
                        "Bác sĩ không tồn tại hoặc đang ngừng hoạt động."));
    }

    private void ensureNoActiveAppointments(DoctorTimeOff item) {
        UUID staffId = item.getDoctorProfile().getStaffAccount().getId();
        boolean hasActive = appointmentRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatusIn(staffId,
                item.getStartDate(), item.getEndDate(), List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN))
                .stream().anyMatch(appointment -> appointment.getStatus() == AppointmentStatus.BOOKED
                        || appointment.getStatus() == AppointmentStatus.CHECKED_IN);
        if (hasActive) {
            throw conflict("DOCTOR_TIME_OFF_HAS_ACTIVE_APPOINTMENTS",
                    "Không thể sửa hoặc hủy lịch nghỉ khi còn lượt khám đã đặt hoặc đã tiếp nhận; hãy xử lý lịch bệnh nhân trước.");
        }
    }

    private int reopenSlots(DoctorTimeOff item) {
        UUID staffId = item.getDoctorProfile().getStaffAccount().getId();
        List<GeneratedClinicSlot> candidates = slotRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenAndStatusAndCancellationReason(staffId,
                        item.getStartDate(), item.getEndDate(), GeneratedSlotStatus.CANCELLED,
                        GeneratedSlotCancellationReason.DOCTOR_TIME_OFF);
        List<GeneratedClinicSlot> reopened = candidates.stream()
                .filter(slot -> !timeOffRepository.existsActiveOnDate(item.getDoctorProfile().getId(), slot.getAppointmentDate()))
                .filter(slot -> appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatusIn(
                        staffId, slot.getAppointmentDate(), slot.getStartTime(),
                        List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN)) == 0)
                .filter(GeneratedClinicSlot::reopenAfterDoctorTimeOff)
                .toList();
        if (!reopened.isEmpty()) slotRepository.saveAll(reopened);
        return reopened.size();
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    @Transactional(readOnly = true)
    public List<DoctorTimeOffResponse> list() {
        return timeOffRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .map(item -> DoctorTimeOffResponse.from(item, 0, 0, 0))
                .toList();
    }

    private void validate(CreateDoctorTimeOffRequest request) {
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        if (request == null || request.startDate() == null || request.endDate() == null
                || request.startDate().isBefore(today)
                || request.startDate().isAfter(request.endDate())
                || ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > MAX_DAYS) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_TIME_OFF_RANGE_INVALID",
                    "Khoảng nghỉ phải nằm trong tương lai hoặc hôm nay và tối đa 30 ngày.");
        }
        if (request.reason() == null || request.reason().trim().length() < 10
                || request.reason().trim().length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_TIME_OFF_REASON_INVALID",
                    "Lý do nghỉ phải có từ 10 đến 500 ký tự.");
        }
    }
}

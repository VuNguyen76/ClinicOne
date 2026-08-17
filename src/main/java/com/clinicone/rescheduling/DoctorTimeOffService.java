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
        DoctorProfile doctor = doctorRepository.findByStaffAccount_Id(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_AVAILABLE",
                        "Bác sĩ không tồn tại hoặc đang ngừng hoạt động."));

        DoctorTimeOff timeOff = timeOffRepository.save(DoctorTimeOff.create(doctor, request.startDate(),
                request.endDate(), request.reason()));
        List<GeneratedClinicSlot> openSlots = slotRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(request.doctorId(), request.startDate(),
                        request.endDate(), GeneratedSlotStatus.OPEN);
        List<GeneratedClinicSlot> lockedSlots = openSlots.stream()
                .filter(slot -> appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        request.doctorId(), slot.getAppointmentDate(), slot.getStartTime(), AppointmentStatus.BOOKED) == 0)
                .toList();
        lockedSlots.forEach(GeneratedClinicSlot::cancel);
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

    @Transactional(readOnly = true)
    public List<DoctorTimeOffResponse> list() {
        return timeOffRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .map(item -> DoctorTimeOffResponse.from(item, 0, 0, 0))
                .toList();
    }

    private void validate(CreateDoctorTimeOffRequest request) {
        if (request == null || request.startDate() == null || request.endDate() == null
                || request.startDate().isAfter(request.endDate())
                || ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > MAX_DAYS) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_TIME_OFF_RANGE_INVALID",
                    "Khoảng nghỉ phải nằm trong cùng một khoảng tối đa 30 ngày.");
        }
        if (request.reason() == null || request.reason().trim().length() < 10
                || request.reason().trim().length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_TIME_OFF_REASON_INVALID",
                    "Lý do nghỉ phải có từ 10 đến 500 ký tự.");
        }
    }
}

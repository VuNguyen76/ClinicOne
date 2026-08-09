package com.clinicone.schedule;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentAvailabilityService {
    private static final int SLOT_CAPACITY = 10;
    private static final String DEFAULT_DOCTOR = "Bác sĩ chuyên khoa";
    private static final List<SlotTemplate> SLOT_TEMPLATES = List.of(
            new SlotTemplate(LocalTime.of(7, 30), LocalTime.of(8, 30)),
            new SlotTemplate(LocalTime.of(8, 30), LocalTime.of(9, 30)),
            new SlotTemplate(LocalTime.of(9, 30), LocalTime.of(10, 30)),
            new SlotTemplate(LocalTime.of(10, 30), LocalTime.of(11, 30)),
            new SlotTemplate(LocalTime.of(13, 0), LocalTime.of(14, 0)),
            new SlotTemplate(LocalTime.of(14, 0), LocalTime.of(15, 0)),
            new SlotTemplate(LocalTime.of(15, 0), LocalTime.of(16, 0))
    );

    private final AppointmentRepository appointmentRepository;
    private final SpecialtyCatalogService specialtyCatalog;
    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog) {
        this(appointmentRepository, specialtyCatalog, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository) {
        this.appointmentRepository = appointmentRepository;
        this.specialtyCatalog = specialtyCatalog;
        this.doctorProfileRepository = doctorProfileRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, new SpecialtyCatalogService());
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> find(String specialty, LocalDate from, LocalDate to) {
        specialtyCatalog.require(specialty);
        validateRange(from, to);
        if (doctorProfileRepository != null) {
            return findConfigured(specialty, from, to);
        }
        Map<SlotKey, Long> bookedBySlot = new HashMap<>();
        appointmentRepository.countBookedBySpecialtyAndDateRange(specialty, from, to, AppointmentStatus.BOOKED)
                .forEach(item -> bookedBySlot.put(new SlotKey(item.appointmentDate(), item.startTime()), item.bookedCount()));
        return from.datesUntil(to.plusDays(1))
                .filter(this::isWorkingDay)
                .flatMap(date -> SLOT_TEMPLATES.stream().map(template -> toResponse(specialty, date, template,
                        bookedBySlot.getOrDefault(new SlotKey(date, template.startTime()), 0L))))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, LocalDate appointmentDate, LocalTime startTime) {
        ensureBookableFallback(specialty, appointmentDate, startTime);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                                LocalTime startTime) {
        specialtyCatalog.require(specialty);
        if (doctorProfileRepository == null) {
            ensureBookableFallback(specialty, appointmentDate, startTime);
            return;
        }
        validateDate(appointmentDate);
        if (doctorId == null) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_REQUIRED",
                    "Vui lòng chọn bác sĩ từ một khung giờ đang mở.");
        }
        DoctorProfile profile = doctorProfileRepository.findByStaffAccount_Id(doctorId)
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "DOCTOR_NOT_AVAILABLE",
                        "Bác sĩ không còn lịch làm phù hợp."));
        if (!profile.getSpecialty().equalsIgnoreCase(specialty)
                || (doctorName != null && !profile.getStaffAccount().getFullName().equalsIgnoreCase(doctorName))) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SLOT_INVALID",
                    "Bác sĩ không thuộc chuyên khoa hoặc khung giờ đã chọn.");
        }
        boolean scheduled = doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                        profile.getId(), appointmentDate.getDayOfWeek()).stream()
                .anyMatch(schedule -> contains(schedule, startTime));
        if (!scheduled) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SLOT_INVALID",
                    "Khung giờ này không nằm trong giờ làm của bác sĩ.");
        }
        if (appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                doctorId, appointmentDate, startTime, AppointmentStatus.BOOKED) > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private void ensureBookableFallback(String specialty, LocalDate appointmentDate, LocalTime startTime) {
        specialtyCatalog.require(specialty);
        validateDate(appointmentDate);
        SlotTemplate template = SLOT_TEMPLATES.stream()
                .filter(item -> item.startTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                        "Khung giờ này không thuộc lịch khám đang mở."));
        long booked = appointmentRepository.countBySpecialtyAndAppointmentDateAndStartTimeAndStatus(
                specialty, appointmentDate, template.startTime(), AppointmentStatus.BOOKED);
        AvailableSlotResponse slot = toResponse(specialty, appointmentDate, template, booked);
        if (slot.remainingCapacity() <= 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private List<AvailableSlotResponse> findConfigured(String specialty, LocalDate from, LocalDate to) {
        List<DoctorProfile> profiles = doctorProfileRepository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty);
        return from.datesUntil(to.plusDays(1))
                .flatMap(date -> profiles.stream()
                        .flatMap(profile -> doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                                        profile.getId(), date.getDayOfWeek()).stream()
                                .flatMap(schedule -> slotsFor(profile, schedule, date).stream())))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    private List<AvailableSlotResponse> slotsFor(DoctorProfile profile, DoctorSchedule schedule, LocalDate date) {
        java.util.ArrayList<AvailableSlotResponse> slots = new java.util.ArrayList<>();
        LocalTime start = schedule.getStartTime();
        while (!start.plusMinutes(schedule.getSlotDurationMinutes()).isAfter(schedule.getEndTime())) {
            long booked = appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                    profile.getStaffAccount().getId(), date, start, AppointmentStatus.BOOKED);
            LocalTime end = start.plusMinutes(schedule.getSlotDurationMinutes());
            slots.add(new AvailableSlotResponse(profile.getSpecialty(), date, start, end,
                    profile.getStaffAccount().getFullName(), booked == 0 ? 1 : 0,
                    profile.getStaffAccount().getId(), profile.getRoom().getCode()));
            start = end;
        }
        return slots;
    }

    private boolean contains(DoctorSchedule schedule, LocalTime start) {
        return !start.isBefore(schedule.getStartTime())
                && !start.plusMinutes(schedule.getSlotDurationMinutes()).isAfter(schedule.getEndTime());
    }

    private void validateDate(LocalDate appointmentDate) {
        if (appointmentDate == null || appointmentDate.isBefore(LocalDate.now()) || !isWorkingDay(appointmentDate)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID", "Ngày khám không còn nhận đặt lịch.");
        }
    }

    private AvailableSlotResponse toResponse(String specialty, LocalDate date, SlotTemplate template, long booked) {
        return new AvailableSlotResponse(specialty, date, template.startTime(), template.endTime(), DEFAULT_DOCTOR,
                Math.max(0, SLOT_CAPACITY - Math.toIntExact(booked)), null, null);
    }

    private boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || from.plusDays(31).isBefore(to)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "APPOINTMENT_SLOT_RANGE_INVALID",
                    "Khoảng ngày tìm lịch phải hợp lệ và không vượt quá 31 ngày.");
        }
    }

    private record SlotTemplate(LocalTime startTime, LocalTime endTime) {
    }

    private record SlotKey(LocalDate date, LocalTime startTime) {
    }
}

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final AppointmentHoldRepository holdRepository;
    private final Clock clock;

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog) {
        this(appointmentRepository, specialtyCatalog, null, null, null, Clock.systemUTC());
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository) {
        this(appointmentRepository, specialtyCatalog, doctorProfileRepository, doctorScheduleRepository,
                null, Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository,
                                          AppointmentHoldRepository holdRepository,
                                          Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.specialtyCatalog = specialtyCatalog;
        this.doctorProfileRepository = doctorProfileRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.holdRepository = holdRepository;
        this.clock = clock;
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
        if (holdRepository != null) {
            holdRepository.findActiveBySpecialtyAndDateRange(specialty, from, to, Instant.now(clock))
                    .forEach(hold -> bookedBySlot.merge(
                            new SlotKey(hold.getAppointmentDate(), hold.getStartTime()), 1L, Long::sum));
        }
        return from.datesUntil(to.plusDays(1))
                .filter(this::isWorkingDay)
                .flatMap(date -> SLOT_TEMPLATES.stream().map(template -> toResponse(specialty, date, template,
                        bookedBySlot.getOrDefault(new SlotKey(date, template.startTime()), 0L))))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, LocalDate appointmentDate, LocalTime startTime) {
        ensureBookableFallback(specialty, appointmentDate, startTime, null);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                                LocalTime startTime) {
        ensureBookable(specialty, doctorName, doctorId, appointmentDate, startTime, null);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                               LocalTime startTime, UUID excludedHoldId) {
        specialtyCatalog.require(specialty);
        if (doctorProfileRepository == null) {
            ensureBookableFallback(specialty, appointmentDate, startTime, excludedHoldId);
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
        long booked = appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                doctorId, appointmentDate, startTime, AppointmentStatus.BOOKED);
        long activeHolds = 0;
        if (holdRepository != null) {
            activeHolds = excludedHoldId == null
                    ? holdRepository.countActiveByDoctorSlot(doctorId, appointmentDate, startTime, Instant.now(clock))
                    : holdRepository.countActiveByDoctorSlotExcludingHold(doctorId, appointmentDate, startTime,
                    Instant.now(clock), excludedHoldId);
        }
        if (booked > 0 || activeHolds > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private void ensureBookableFallback(String specialty, LocalDate appointmentDate, LocalTime startTime,
                                        UUID excludedHoldId) {
        specialtyCatalog.require(specialty);
        validateDate(appointmentDate);
        SlotTemplate template = SLOT_TEMPLATES.stream()
                .filter(item -> item.startTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                        "Khung giờ này không thuộc lịch khám đang mở."));
        long booked = appointmentRepository.countBySpecialtyAndAppointmentDateAndStartTimeAndStatus(
                specialty, appointmentDate, template.startTime(), AppointmentStatus.BOOKED);
        if (holdRepository != null) {
            booked += excludedHoldId == null
                    ? holdRepository.countActiveBySpecialtyAndSlot(specialty, appointmentDate, template.startTime(),
                    Instant.now(clock))
                    : holdRepository.countActiveBySpecialtyAndSlotExcludingHold(specialty, appointmentDate,
                    template.startTime(), Instant.now(clock), excludedHoldId);
        }
        AvailableSlotResponse slot = toResponse(specialty, appointmentDate, template, booked);
        if (slot.remainingCapacity() <= 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private List<AvailableSlotResponse> findConfigured(String specialty, LocalDate from, LocalDate to) {
        List<DoctorSchedule> schedules = doctorScheduleRepository.findActiveBySpecialtyIgnoreCase(specialty);
        List<UUID> doctorStaffIds = schedules.stream()
                .map(schedule -> schedule.getDoctorProfile().getStaffAccount().getId())
                .distinct()
                .toList();
        if (doctorStaffIds.isEmpty()) {
            return List.of();
        }
        Map<DoctorSlotKey, Long> bookedBySlot = appointmentRepository
                .countBookedByDoctorsAndDateRange(doctorStaffIds, from, to, AppointmentStatus.BOOKED)
                .stream()
                .collect(Collectors.toMap(
                        item -> new DoctorSlotKey(item.doctorStaffId(), item.appointmentDate(), item.startTime()),
                        DoctorSlotBookingCount::bookedCount));
        if (holdRepository != null) {
            holdRepository.findActiveByDoctorsAndDateRange(doctorStaffIds, from, to, Instant.now(clock))
                    .forEach(hold -> bookedBySlot.merge(
                            new DoctorSlotKey(hold.getDoctorStaffId(), hold.getAppointmentDate(), hold.getStartTime()),
                            1L, Long::sum));
        }
        return from.datesUntil(to.plusDays(1))
                .flatMap(date -> schedules.stream()
                        .filter(schedule -> schedule.getDayOfWeek() == date.getDayOfWeek())
                        .flatMap(schedule -> slotsFor(schedule, date, bookedBySlot).stream()))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    private List<AvailableSlotResponse> slotsFor(DoctorSchedule schedule, LocalDate date,
                                                  Map<DoctorSlotKey, Long> bookedBySlot) {
        DoctorProfile profile = schedule.getDoctorProfile();
        UUID doctorStaffId = profile.getStaffAccount().getId();
        ArrayList<AvailableSlotResponse> slots = new ArrayList<>();
        LocalTime start = schedule.getStartTime();
        while (!start.plusMinutes(schedule.getSlotDurationMinutes()).isAfter(schedule.getEndTime())) {
            long booked = bookedBySlot.getOrDefault(new DoctorSlotKey(doctorStaffId, date, start), 0L);
            LocalTime end = start.plusMinutes(schedule.getSlotDurationMinutes());
            slots.add(new AvailableSlotResponse(profile.getSpecialty(), date, start, end,
                    profile.getStaffAccount().getFullName(), booked == 0 ? 1 : 0,
                    doctorStaffId, profile.getRoom().getCode()));
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

    private record DoctorSlotKey(UUID doctorStaffId, LocalDate date, LocalTime startTime) {
    }
}

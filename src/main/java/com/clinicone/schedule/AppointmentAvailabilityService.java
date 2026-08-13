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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentAvailabilityService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<AppointmentStatus> ACTIVE_BOOKING_STATUSES =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN);
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
    private final ClinicServiceRepository clinicServiceRepository;
    private final GeneratedClinicSlotRepository generatedSlotRepository;

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog) {
        this(appointmentRepository, specialtyCatalog, null, null, null, Clock.systemUTC(), null, null);
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository) {
        this(appointmentRepository, specialtyCatalog, doctorProfileRepository, doctorScheduleRepository,
                null, Clock.systemUTC(), null, null);
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository,
                                          AppointmentHoldRepository holdRepository,
                                          Clock clock) {
        this(appointmentRepository, specialtyCatalog, doctorProfileRepository, doctorScheduleRepository,
                holdRepository, clock, null, null);
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository,
                                          AppointmentHoldRepository holdRepository,
                                          Clock clock,
                                          ClinicServiceRepository clinicServiceRepository) {
        this(appointmentRepository, specialtyCatalog, doctorProfileRepository, doctorScheduleRepository,
                holdRepository, clock, clinicServiceRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog,
                                          DoctorProfileRepository doctorProfileRepository,
                                          DoctorScheduleRepository doctorScheduleRepository,
                                          AppointmentHoldRepository holdRepository,
                                          Clock clock,
                                          ClinicServiceRepository clinicServiceRepository,
                                          GeneratedClinicSlotRepository generatedSlotRepository) {
        this.appointmentRepository = appointmentRepository;
        this.specialtyCatalog = specialtyCatalog;
        this.doctorProfileRepository = doctorProfileRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.holdRepository = holdRepository;
        this.clock = clock;
        this.clinicServiceRepository = clinicServiceRepository;
        this.generatedSlotRepository = generatedSlotRepository;
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, new SpecialtyCatalogService());
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> find(String specialty, LocalDate from, LocalDate to) {
        return find(specialty, from, to, null);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> find(String specialty, LocalDate from, LocalDate to, UUID serviceId) {
        specialtyCatalog.require(specialty);
        validateRange(from, to);
        ClinicService service = resolveService(serviceId, specialty);
        Integer serviceDuration = service == null ? null : service.getDurationMinutes();
        if (serviceId != null && generatedSlotRepository != null) {
            List<GeneratedClinicSlot> generatedSlots = generatedSlotRepository
                    .findByClinicServiceIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                            serviceId, from, to);
            if (!generatedSlots.isEmpty()) {
                List<GeneratedClinicSlot> openSlots = generatedSlots.stream()
                        .filter(slot -> slot.getStatus() == GeneratedSlotStatus.OPEN)
                        .toList();
                return findGenerated(openSlots, from, to);
            }
        }
        if (doctorProfileRepository != null) {
            return findConfigured(specialty, from, to, service);
        }
        Map<SlotKey, Long> bookedBySlot = new HashMap<>();
        countSpecialtyBookings(specialty, from, to).forEach(item ->
                bookedBySlot.put(new SlotKey(item.appointmentDate(), item.startTime()), item.bookedCount()));
        if (holdRepository != null) {
            holdRepository.findActiveBySpecialtyAndDateRange(specialty, from, to, Instant.now(clock))
                    .forEach(hold -> bookedBySlot.merge(
                            new SlotKey(hold.getAppointmentDate(), hold.getStartTime()), 1L, Long::sum));
        }
        return from.datesUntil(to.plusDays(1))
                .filter(this::isWorkingDay)
                .flatMap(date -> slotTemplates(serviceDuration).stream().map(template -> toResponse(specialty, date, template,
                        bookedBySlot.getOrDefault(new SlotKey(date, template.startTime()), 0L))))
                .filter(slot -> isFutureSlot(slot.appointmentDate(), slot.startTime()))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, LocalDate appointmentDate, LocalTime startTime) {
        ensureBookable(specialty, null, null, appointmentDate, startTime, null, null);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                                LocalTime startTime) {
        ensureBookable(specialty, doctorName, doctorId, appointmentDate, startTime, null, null);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                               LocalTime startTime, UUID excludedHoldId) {
        ensureBookable(specialty, doctorName, doctorId, appointmentDate, startTime, excludedHoldId, null);
    }

    @Transactional(readOnly = true)
    public void ensureBookable(String specialty, String doctorName, UUID doctorId, LocalDate appointmentDate,
                               LocalTime startTime, UUID excludedHoldId, UUID serviceId) {
        specialtyCatalog.require(specialty);
        ClinicService service = resolveService(serviceId, specialty);
        Integer serviceDuration = service == null ? null : service.getDurationMinutes();
        validateDate(appointmentDate);
        LocalDate clinicToday = LocalDate.now(clock.withZone(CLINIC_ZONE));
        if (appointmentDate.equals(clinicToday)
                && (startTime == null || !startTime.isAfter(LocalTime.now(clock.withZone(CLINIC_ZONE))))) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID", "Khung gio nay da qua.");
        }
        if (serviceId != null && generatedSlotRepository != null && doctorId != null) {
            List<GeneratedClinicSlot> generatedSlots = generatedSlotRepository
                    .findByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTime(
                            serviceId, doctorId, appointmentDate, startTime);
            if (!generatedSlots.isEmpty()) {
                GeneratedClinicSlot openSlot = generatedSlots.stream()
                        .filter(slot -> slot.getStatus() == GeneratedSlotStatus.OPEN)
                        .findFirst()
                        .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                                "Khung giờ đã đóng và không còn nhận đặt lịch."));
                ensureGeneratedSlotBookable(openSlot, doctorName, excludedHoldId);
                return;
            }
            // Once a service/doctor has generated slots for the requested day,
            // the generated set is the source of truth. Never fall back to the
            // older weekly schedule for a time that was not generated.
            if (!generatedSlotRepository.findByClinicServiceIdAndDoctorStaffIdAndAppointmentDate(
                    serviceId, doctorId, appointmentDate).isEmpty()) {
                throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                        "Khung giờ này không nằm trong các khung giờ đang mở.");
            }
        }
        if (doctorProfileRepository == null) {
            ensureBookableFallback(specialty, appointmentDate, startTime, excludedHoldId, serviceDuration);
            return;
        }
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
                .anyMatch(schedule -> contains(schedule, startTime, serviceDuration));
        if (!scheduled) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SLOT_INVALID",
                    "Khung giờ này không nằm trong giờ làm của bác sĩ.");
        }
        long booked = countDoctorBookings(doctorId, appointmentDate, startTime);
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
                                        UUID excludedHoldId, Integer serviceDuration) {
        specialtyCatalog.require(specialty);
        validateDate(appointmentDate);
        if (!isWorkingDay(appointmentDate)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                    "Khung giờ này không thuộc lịch khám đang mở.");
        }
        SlotTemplate template = slotTemplates(serviceDuration).stream()
                .filter(item -> item.startTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                        "Khung giờ này không thuộc lịch khám đang mở."));
        long booked = countSpecialtyBookings(specialty, appointmentDate, template.startTime());
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

    private List<AvailableSlotResponse> findConfigured(String specialty, LocalDate from, LocalDate to,
                                                       ClinicService service) {
        List<DoctorSchedule> schedules = doctorScheduleRepository.findActiveBySpecialtyIgnoreCase(specialty);
        if (service != null && service.getEligibleDoctors() != null && !service.getEligibleDoctors().isEmpty()) {
            var eligibleDoctorIds = service.getEligibleDoctors().stream()
                    .map(doctor -> doctor.getStaffAccount().getId())
                    .collect(Collectors.toSet());
            schedules = schedules.stream()
                    .filter(schedule -> eligibleDoctorIds.contains(schedule.getDoctorProfile().getStaffAccount().getId()))
                    .toList();
        }
        List<UUID> doctorStaffIds = schedules.stream()
                .map(schedule -> schedule.getDoctorProfile().getStaffAccount().getId())
                .distinct()
                .toList();
        if (doctorStaffIds.isEmpty()) {
            return List.of();
        }
        Map<DoctorSlotKey, Long> bookedBySlot = countDoctorBookings(doctorStaffIds, from, to);
        if (holdRepository != null) {
            holdRepository.findActiveByDoctorsAndDateRange(doctorStaffIds, from, to, Instant.now(clock))
                    .forEach(hold -> bookedBySlot.merge(
                            new DoctorSlotKey(hold.getDoctorStaffId(), hold.getAppointmentDate(), hold.getStartTime()),
                            1L, Long::sum));
        }
        List<DoctorSchedule> configuredSchedules = schedules;
        Integer serviceDuration = service == null ? null : service.getDurationMinutes();
        return from.datesUntil(to.plusDays(1))
                .flatMap(date -> configuredSchedules.stream()
                        .filter(schedule -> schedule.getDayOfWeek() == date.getDayOfWeek())
                        .flatMap(schedule -> slotsFor(schedule, date, bookedBySlot,
                                serviceDuration).stream()))
                .filter(slot -> isFutureSlot(slot.appointmentDate(), slot.startTime()))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    private List<AvailableSlotResponse> findGenerated(List<GeneratedClinicSlot> generatedSlots,
                                                      LocalDate from, LocalDate to) {
        List<UUID> doctorStaffIds = generatedSlots.stream().map(GeneratedClinicSlot::getDoctorStaffId)
                .distinct().toList();
        Map<DoctorSlotKey, Long> bookedBySlot = countDoctorBookings(doctorStaffIds, from, to);
        if (holdRepository != null) {
            holdRepository.findActiveByDoctorsAndDateRange(doctorStaffIds, from, to, Instant.now(clock))
                    .forEach(hold -> bookedBySlot.merge(
                            new DoctorSlotKey(hold.getDoctorStaffId(), hold.getAppointmentDate(), hold.getStartTime()),
                            1L, Long::sum));
        }
        return generatedSlots.stream()
                .map(slot -> new AvailableSlotResponse(slot.getSpecialty(), slot.getAppointmentDate(),
                        slot.getStartTime(), slot.getEndTime(), slot.getDoctorName(),
                        bookedBySlot.getOrDefault(new DoctorSlotKey(slot.getDoctorStaffId(),
                                slot.getAppointmentDate(), slot.getStartTime()), 0L) == 0 ? 1 : 0,
                        slot.getDoctorStaffId(), slot.getRoomCode()))
                .filter(slot -> isFutureSlot(slot.appointmentDate(), slot.startTime()))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    private void ensureGeneratedSlotBookable(GeneratedClinicSlot slot, String doctorName, UUID excludedHoldId) {
        if (doctorName != null && !slot.getDoctorName().equalsIgnoreCase(doctorName)) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SLOT_INVALID",
                    "Bác sĩ không thuộc khung giờ đã chọn.");
        }
        long booked = countDoctorBookings(slot.getDoctorStaffId(), slot.getAppointmentDate(), slot.getStartTime());
        if (holdRepository != null) {
            booked += excludedHoldId == null
                    ? holdRepository.countActiveByDoctorSlot(slot.getDoctorStaffId(), slot.getAppointmentDate(),
                    slot.getStartTime(), Instant.now(clock))
                    : holdRepository.countActiveByDoctorSlotExcludingHold(slot.getDoctorStaffId(),
                    slot.getAppointmentDate(), slot.getStartTime(), Instant.now(clock), excludedHoldId);
        }
        if (booked > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private List<AvailableSlotResponse> slotsFor(DoctorSchedule schedule, LocalDate date,
                                                  Map<DoctorSlotKey, Long> bookedBySlot, Integer serviceDuration) {
        DoctorProfile profile = schedule.getDoctorProfile();
        UUID doctorStaffId = profile.getStaffAccount().getId();
        ArrayList<AvailableSlotResponse> slots = new ArrayList<>();
        int duration = serviceDuration == null ? schedule.getSlotDurationMinutes() : serviceDuration;
        LocalTime start = schedule.getStartTime();
        while (!start.plusMinutes(duration).isAfter(schedule.getEndTime())) {
            long booked = bookedBySlot.getOrDefault(new DoctorSlotKey(doctorStaffId, date, start), 0L);
            LocalTime end = start.plusMinutes(duration);
            slots.add(new AvailableSlotResponse(profile.getSpecialty(), date, start, end,
                    profile.getStaffAccount().getFullName(), booked == 0 ? 1 : 0,
                    doctorStaffId, profile.getRoom().getCode()));
            start = end;
        }
        return slots;
    }

    private boolean contains(DoctorSchedule schedule, LocalTime start, Integer serviceDuration) {
        int duration = serviceDuration == null ? schedule.getSlotDurationMinutes() : serviceDuration;
        return !start.isBefore(schedule.getStartTime())
                && !start.plusMinutes(duration).isAfter(schedule.getEndTime());
    }

    private void validateDate(LocalDate appointmentDate) {
        LocalDate clinicToday = LocalDate.now(clock.withZone(CLINIC_ZONE));
        if (appointmentDate == null || appointmentDate.isBefore(clinicToday)
                || appointmentDate.isAfter(clinicToday.plusDays(30))) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                    "Ngày khám phải nằm trong 30 ngày kể từ hôm nay.");
        }
    }

    private AvailableSlotResponse toResponse(String specialty, LocalDate date, SlotTemplate template, long booked) {
        return new AvailableSlotResponse(specialty, date, template.startTime(), template.endTime(), DEFAULT_DOCTOR,
                Math.max(0, SLOT_CAPACITY - Math.toIntExact(booked)), null, null);
    }

    private boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private boolean isFutureSlot(LocalDate date, LocalTime startTime) {
        LocalDate clinicToday = LocalDate.now(clock.withZone(CLINIC_ZONE));
        return date != null && startTime != null
                && (date.isAfter(clinicToday)
                || (date.equals(clinicToday) && startTime.isAfter(LocalTime.now(clock.withZone(CLINIC_ZONE)))));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        // The interval is inclusive of both dates, but the SRS limit is a
        // maximum of 30 days from the requested start date. A 31-day gap
        // would expose 32 calendar dates and must be rejected.
        if (from == null || to == null || from.isAfter(to) || from.plusDays(30).isBefore(to)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "APPOINTMENT_SLOT_RANGE_INVALID",
                    "Khoảng ngày tìm lịch phải hợp lệ và không vượt quá 30 ngày kể từ ngày bắt đầu.");
        }
    }

    private record SlotTemplate(LocalTime startTime, LocalTime endTime) {
    }

    private record SlotKey(LocalDate date, LocalTime startTime) {
    }

    private List<SlotTemplate> slotTemplates(Integer serviceDuration) {
        if (serviceDuration == null || serviceDuration == 60) {
            return SLOT_TEMPLATES;
        }
        List<SlotTemplate> result = new ArrayList<>();
        addTemplates(result, LocalTime.of(7, 30), LocalTime.of(11, 30), serviceDuration);
        addTemplates(result, LocalTime.of(13, 0), LocalTime.of(16, 0), serviceDuration);
        return result;
    }

    private void addTemplates(List<SlotTemplate> target, LocalTime windowStart, LocalTime windowEnd, int duration) {
        LocalTime start = windowStart;
        while (!start.plusMinutes(duration).isAfter(windowEnd)) {
            target.add(new SlotTemplate(start, start.plusMinutes(duration)));
            start = start.plusMinutes(duration);
        }
    }

    private ClinicService resolveService(UUID serviceId, String specialty) {
        if (serviceId == null) {
            return null;
        }
        if (clinicServiceRepository == null) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_UNAVAILABLE",
                    "Danh mục dịch vụ khám chưa sẵn sàng.");
        }
        ClinicService service = clinicServiceRepository.findById(serviceId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CLINIC_SERVICE_NOT_FOUND",
                        "Không tìm thấy dịch vụ khám đã chọn."));
        if (!service.isActive()) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_INACTIVE",
                    "Dịch vụ khám đã tạm ngưng nhận lịch.");
        }
        if (!service.getSpecialty().equalsIgnoreCase(specialty.trim())) {
            throw new AuthException(HttpStatus.CONFLICT, "CLINIC_SERVICE_SPECIALTY_MISMATCH",
                    "Dịch vụ không thuộc chuyên khoa đã chọn.");
        }
        return service;
    }

    private record DoctorSlotKey(UUID doctorStaffId, LocalDate date, LocalTime startTime) {
    }

    private List<SlotBookingCount> countSpecialtyBookings(String specialty, LocalDate from, LocalDate to) {
        return appointmentRepository.countBookedBySpecialtyAndDateRangeAndStatusIn(
                specialty, from, to, ACTIVE_BOOKING_STATUSES);
    }

    private long countSpecialtyBookings(String specialty, LocalDate date, LocalTime startTime) {
        return appointmentRepository.countBySpecialtyAndAppointmentDateAndStartTimeAndStatusIn(
                specialty, date, startTime, ACTIVE_BOOKING_STATUSES);
    }

    private long countDoctorBookings(UUID doctorId, LocalDate date, LocalTime startTime) {
        return appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatusIn(
                doctorId, date, startTime, ACTIVE_BOOKING_STATUSES);
    }

    private Map<DoctorSlotKey, Long> countDoctorBookings(List<UUID> doctorStaffIds, LocalDate from, LocalDate to) {
        return appointmentRepository.countBookedByDoctorsAndDateRangeAndStatusIn(
                        doctorStaffIds, from, to, ACTIVE_BOOKING_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        item -> new DoctorSlotKey(item.doctorStaffId(), item.appointmentDate(), item.startTime()),
                        DoctorSlotBookingCount::bookedCount));
    }
}

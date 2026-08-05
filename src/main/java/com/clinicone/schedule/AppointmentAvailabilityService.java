package com.clinicone.schedule;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
                                          SpecialtyCatalogService specialtyCatalog) {
        this.appointmentRepository = appointmentRepository;
        this.specialtyCatalog = specialtyCatalog;
    }

    public AppointmentAvailabilityService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, new SpecialtyCatalogService());
    }

    public List<AvailableSlotResponse> find(String specialty, LocalDate from, LocalDate to) {
        specialtyCatalog.require(specialty);
        validateRange(from, to);
        return from.datesUntil(to.plusDays(1))
                .filter(this::isWorkingDay)
                .flatMap(date -> SLOT_TEMPLATES.stream().map(template -> toResponse(specialty, date, template)))
                .filter(slot -> slot.remainingCapacity() > 0)
                .toList();
    }

    public void ensureBookable(String specialty, LocalDate appointmentDate, LocalTime startTime) {
        specialtyCatalog.require(specialty);
        if (appointmentDate == null || appointmentDate.isBefore(LocalDate.now()) || !isWorkingDay(appointmentDate)) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID", "Ngày khám không còn nhận đặt lịch.");
        }
        SlotTemplate template = SLOT_TEMPLATES.stream()
                .filter(item -> item.startTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_INVALID",
                        "Khung giờ này không thuộc lịch khám đang mở."));
        AvailableSlotResponse slot = toResponse(specialty, appointmentDate, template);
        if (slot.remainingCapacity() <= 0) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_FULL",
                    "Khung giờ này vừa hết chỗ. Vui lòng chọn khung giờ khác.");
        }
    }

    private AvailableSlotResponse toResponse(String specialty, LocalDate date, SlotTemplate template) {
        long booked = appointmentRepository.countBySpecialtyAndAppointmentDateAndStartTimeAndStatus(
                specialty, date, template.startTime(), AppointmentStatus.BOOKED);
        return new AvailableSlotResponse(specialty, date, template.startTime(), template.endTime(), DEFAULT_DOCTOR,
                Math.max(0, SLOT_CAPACITY - Math.toIntExact(booked)));
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
}

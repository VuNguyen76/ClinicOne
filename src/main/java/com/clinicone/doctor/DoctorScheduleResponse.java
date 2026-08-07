package com.clinicone.doctor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorScheduleResponse(UUID id, UUID doctorId, DayOfWeek dayOfWeek, LocalTime startTime,
                                     LocalTime endTime, int slotDurationMinutes, boolean active) {
    public static DoctorScheduleResponse from(DoctorSchedule schedule) {
        return new DoctorScheduleResponse(schedule.getId(), schedule.getDoctorProfile().getStaffAccount().getId(),
                schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getSlotDurationMinutes(), schedule.isActive());
    }
}

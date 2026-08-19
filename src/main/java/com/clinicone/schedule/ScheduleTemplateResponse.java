package com.clinicone.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ScheduleTemplateResponse(
        UUID id,
        UUID clinicServiceId,
        String serviceName,
        String specialty,
        String visitType,
        int durationMinutes,
        UUID doctorId,
        String doctorName,
        String doctorAvatarUrl,
        UUID roomId,
        String roomCode,
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> weekdays,
        LocalTime dayStart,
        LocalTime dayEnd,
        List<ScheduleBreakResponse> breaks,
        Set<LocalDate> exceptionDates,
        int generatedSlotCount,
        boolean active
) {
}

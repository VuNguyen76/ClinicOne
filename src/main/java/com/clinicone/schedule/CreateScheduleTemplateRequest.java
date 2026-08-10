package com.clinicone.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateScheduleTemplateRequest(
        @NotNull UUID clinicServiceId,
        @NotNull UUID doctorId,
        @NotNull UUID roomId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Size(min = 1, max = 7) Set<DayOfWeek> weekdays,
        @NotNull LocalTime dayStart,
        @NotNull LocalTime dayEnd,
        @NotNull @Min(5) @Max(120) Integer durationMinutes,
        @Size(max = 5) List<ScheduleBreakRequest> breaks,
        @Size(max = 100) Set<LocalDate> exceptionDates
) {
}

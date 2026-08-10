package com.clinicone.schedule;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ScheduleBreakRequest(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {
}

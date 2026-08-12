package com.clinicone.schedule;

import java.time.LocalTime;

public record ScheduleBreakResponse(LocalTime startTime, LocalTime endTime) {
    static ScheduleBreakResponse from(ScheduleBreak item) {
        return new ScheduleBreakResponse(item.getStartTime(), item.getEndTime());
    }
}

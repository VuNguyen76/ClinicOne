package com.clinicone.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;

@Embeddable
public class ScheduleBreak {
    @Column(name = "break_start", nullable = false)
    private LocalTime startTime;

    @Column(name = "break_end", nullable = false)
    private LocalTime endTime;

    protected ScheduleBreak() {
    }

    private ScheduleBreak(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static ScheduleBreak create(LocalTime startTime, LocalTime endTime) {
        return new ScheduleBreak(startTime, endTime);
    }

    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}

package com.clinicone.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotBookingCount(LocalDate appointmentDate, LocalTime startTime, long bookedCount) {
}

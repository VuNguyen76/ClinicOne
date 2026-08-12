package com.clinicone.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AvailableSlotResponse(
        String specialty,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String doctorName,
        int remainingCapacity,
        UUID doctorId,
        String roomCode
) {
}

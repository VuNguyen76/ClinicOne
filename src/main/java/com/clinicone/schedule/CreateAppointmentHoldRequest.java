package com.clinicone.schedule;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateAppointmentHoldRequest(
        @NotBlank @Size(max = 120) String specialty,
        @NotBlank @Size(max = 120) String doctorName,
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        UUID doctorId
) {
}

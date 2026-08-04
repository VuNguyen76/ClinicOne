package com.clinicone.appointment;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateAppointmentRequest(
        @NotBlank @Size(max = 120) String specialty,
        @NotBlank @Size(max = 120) String doctorName,
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        @NotBlank @Size(min = 3, max = 500) String reason
) {
}

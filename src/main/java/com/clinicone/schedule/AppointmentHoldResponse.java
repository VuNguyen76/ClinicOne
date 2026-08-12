package com.clinicone.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentHoldResponse(
        UUID id,
        String specialty,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        Instant expiresAt,
        UUID serviceId
) {
    static AppointmentHoldResponse from(AppointmentHold hold) {
        return new AppointmentHoldResponse(hold.getId(), hold.getSpecialty(), hold.getDoctorName(),
                hold.getAppointmentDate(), hold.getStartTime(), hold.getExpiresAt(), hold.getServiceId());
    }
}

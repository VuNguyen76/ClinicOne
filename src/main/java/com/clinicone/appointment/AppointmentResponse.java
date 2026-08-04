package com.clinicone.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        String appointmentCode,
        String specialty,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        String reason,
        String status,
        String statusLabel
) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentCode(), appointment.getSpecialty(),
                appointment.getDoctorName(), appointment.getAppointmentDate(), appointment.getStartTime(),
                appointment.getReason(), appointment.getStatus().name(), appointment.getStatus().label());
    }
}

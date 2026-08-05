package com.clinicone.examination;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ExaminationSessionResponse(
        UUID id,
        UUID appointmentId,
        String appointmentCode,
        String specialty,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        String status,
        String statusLabel
) {
    public static ExaminationSessionResponse from(ExaminationSession session) {
        var appointment = session.getAppointment();
        return new ExaminationSessionResponse(session.getId(), appointment.getId(), appointment.getAppointmentCode(),
                appointment.getSpecialty(), appointment.getDoctorName(), appointment.getAppointmentDate(),
                appointment.getStartTime(), session.getStatus().name(), session.getStatus().label());
    }
}

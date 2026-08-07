package com.clinicone.examination;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorExaminationResponse(
        UUID ticketId,
        UUID appointmentId,
        UUID examinationId,
        int queueNumber,
        String roomName,
        String appointmentCode,
        String specialty,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        String patientName,
        LocalDate patientDateOfBirth,
        String patientGender,
        String patientPhone,
        String reason,
        String examinationNotes,
        String diagnosis,
        String conclusion,
        String treatmentPlan,
        String prescription,
        LocalDate followUpDate,
        String status,
        Instant signedAt
) {
}

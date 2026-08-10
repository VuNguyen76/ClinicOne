package com.clinicone.examination;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
        Instant signedAt,
        boolean requiresMedicalRecord,
        List<MedicalRecordResponse> history
) {
    public DoctorExaminationResponse(UUID ticketId, UUID appointmentId, UUID examinationId, int queueNumber,
                                     String roomName, String appointmentCode, String specialty, String doctorName,
                                     LocalDate appointmentDate, LocalTime startTime, String patientName,
                                     LocalDate patientDateOfBirth, String patientGender, String patientPhone,
                                     String reason, String examinationNotes, String diagnosis, String conclusion,
                                     String treatmentPlan, String prescription, LocalDate followUpDate,
                                     String status, Instant signedAt) {
        this(ticketId, appointmentId, examinationId, queueNumber, roomName, appointmentCode, specialty, doctorName,
                appointmentDate, startTime, patientName, patientDateOfBirth, patientGender, patientPhone, reason,
                examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate, status, signedAt,
                true, List.of());
    }
}

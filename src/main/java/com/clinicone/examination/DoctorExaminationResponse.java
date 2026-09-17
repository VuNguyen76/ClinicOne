package com.clinicone.examination;

import java.math.BigDecimal;
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
        UUID clinicServiceId,
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
        Integer followUpDays,
        String followUpNote,
        String status,
        Instant signedAt,
        Instant draftSavedAt,
        Long recordVersion,
        boolean requiresMedicalRecord,
        List<MedicalRecordResponse> history,
        List<PrescriptionLineResponse> prescriptionLines,
        String bloodPressure,
        Integer heartRate,
        BigDecimal temperature,
        Integer spO2,
        BigDecimal weight,
        BigDecimal height,
        String allergySummary
) {
    public DoctorExaminationResponse(UUID ticketId, UUID appointmentId, UUID examinationId, int queueNumber,
                                     String roomName, String appointmentCode, String specialty, String doctorName,
                                     LocalDate appointmentDate, LocalTime startTime, String patientName,
                                     LocalDate patientDateOfBirth, String patientGender, String patientPhone,
                                     String reason, String examinationNotes, String diagnosis, String conclusion,
                                     String treatmentPlan, String prescription, LocalDate followUpDate,
                                     String status, Instant signedAt) {
        this(ticketId, appointmentId, examinationId, queueNumber, roomName, appointmentCode, specialty, null,
                doctorName,
                appointmentDate, startTime, patientName, patientDateOfBirth, patientGender, patientPhone, reason,
                examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate, null, null,
                status, signedAt, null, null, true, List.of(), List.of(),
                null, null, null, null, null, null, null);
    }

    public DoctorExaminationResponse(UUID ticketId, UUID appointmentId, UUID examinationId, int queueNumber,
                                     String roomName, String appointmentCode, String specialty, UUID clinicServiceId,
                                     String doctorName, LocalDate appointmentDate, LocalTime startTime,
                                     String patientName, LocalDate patientDateOfBirth, String patientGender,
                                     String patientPhone, String reason, String examinationNotes, String diagnosis,
                                     String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                                     Integer followUpDays, String followUpNote, String status, Instant signedAt,
                                     Instant draftSavedAt, Long recordVersion, boolean requiresMedicalRecord,
                                     List<MedicalRecordResponse> history, List<PrescriptionLineResponse> prescriptionLines) {
        this(ticketId, appointmentId, examinationId, queueNumber, roomName, appointmentCode, specialty, clinicServiceId,
                doctorName, appointmentDate, startTime, patientName, patientDateOfBirth, patientGender, patientPhone,
                reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                followUpDays, followUpNote, status, signedAt, draftSavedAt, recordVersion, requiresMedicalRecord,
                history, prescriptionLines, null, null, null, null, null, null, null);
    }
}

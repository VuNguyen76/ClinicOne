package com.clinicone.examination;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID examinationId,
        String appointmentCode,
        String doctorName,
        String reason,
        String examinationNotes,
        String diagnosis,
        String conclusion,
        String treatmentPlan,
        String prescription,
        LocalDate followUpDate,
        Instant signedAt
) {
    public static MedicalRecordResponse from(MedicalRecord record) {
        var session = record.getSession();
        var appointment = session == null ? null : session.getAppointment();
        return new MedicalRecordResponse(record.getId(), session == null ? null : session.getId(),
                appointment == null ? null : appointment.getAppointmentCode(), record.getDoctorName(), record.getReason(),
                record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(), record.getTreatmentPlan(),
                record.getPrescription(), record.getFollowUpDate(), record.getSignedAt());
    }
}

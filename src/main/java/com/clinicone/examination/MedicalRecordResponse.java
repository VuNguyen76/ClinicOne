package com.clinicone.examination;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

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
        Integer followUpDays,
        String followUpNote,
        Instant signedAt,
        List<PrescriptionLineResponse> prescriptionLines
) {
    public MedicalRecordResponse(UUID id, UUID examinationId, String appointmentCode, String doctorName,
                                 String reason, String examinationNotes, String diagnosis, String conclusion,
                                 String treatmentPlan, String prescription, LocalDate followUpDate, Instant signedAt) {
        this(id, examinationId, appointmentCode, doctorName, reason, examinationNotes, diagnosis, conclusion,
                treatmentPlan, prescription, followUpDate, null, null, signedAt, List.of());
    }

    public static MedicalRecordResponse from(MedicalRecord record) {
        var session = record.getSession();
        var appointment = session == null ? null : session.getAppointment();
        return new MedicalRecordResponse(record.getId(), session == null ? null : session.getId(),
                appointment == null ? null : appointment.getAppointmentCode(), record.getDoctorName(), record.getReason(),
                record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(), record.getTreatmentPlan(),
                record.getPrescription(), record.getFollowUpDate(), record.getFollowUpDays(), record.getFollowUpNote(), record.getSignedAt(),
                record.getPrescriptionLines().stream().map(PrescriptionLineResponse::from).toList());
    }
}

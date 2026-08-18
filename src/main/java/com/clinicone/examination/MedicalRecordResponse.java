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
        List<PrescriptionLineResponse> prescriptionLines,
        String patientName,
        LocalDate patientDateOfBirth,
        String patientGender,
        String patientPhone,
        String specialty,
        LocalDate appointmentDate
) {
    public MedicalRecordResponse(UUID id, UUID examinationId, String appointmentCode, String doctorName,
                                 String reason, String examinationNotes, String diagnosis, String conclusion,
                                 String treatmentPlan, String prescription, LocalDate followUpDate, Instant signedAt) {
        this(id, examinationId, appointmentCode, doctorName, reason, examinationNotes, diagnosis, conclusion,
                treatmentPlan, prescription, followUpDate, null, null, signedAt, List.of(),
                null, null, null, null, null, null);
    }

    public MedicalRecordResponse(UUID id, UUID examinationId, String appointmentCode, String doctorName,
                                 String reason, String examinationNotes, String diagnosis, String conclusion,
                                 String treatmentPlan, String prescription, LocalDate followUpDate, Integer followUpDays,
                                 String followUpNote, Instant signedAt, List<PrescriptionLineResponse> prescriptionLines) {
        this(id, examinationId, appointmentCode, doctorName, reason, examinationNotes, diagnosis, conclusion,
                treatmentPlan, prescription, followUpDate, followUpDays, followUpNote, signedAt, prescriptionLines,
                null, null, null, null, null, null);
    }

    public static MedicalRecordResponse from(MedicalRecord record) {
        return from(record, true);
    }

    public static MedicalRecordResponse fromSummary(MedicalRecord record) {
        return from(record, false);
    }

    private static MedicalRecordResponse from(MedicalRecord record, boolean includePrescriptionLines) {
        var session = record.getSession();
        var appointment = session == null ? null : session.getAppointment();
        var profile = appointment == null ? null : appointment.getPatientProfile();
        return new MedicalRecordResponse(record.getId(), session == null ? null : session.getId(),
                appointment == null ? null : appointment.getAppointmentCode(), record.getDoctorName(), record.getReason(),
                record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(), record.getTreatmentPlan(),
                record.getPrescription(), record.getFollowUpDate(), record.getFollowUpDays(), record.getFollowUpNote(), record.getSignedAt(),
                includePrescriptionLines
                        ? record.getPrescriptionLines().stream().map(PrescriptionLineResponse::from).toList()
                        : List.of(),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getDateOfBirth(),
                profile == null ? null : profile.getGender(),
                profile == null ? null : profile.getPhone(),
                appointment == null ? null : appointment.getSpecialty(),
                appointment == null ? null : appointment.getAppointmentDate());
    }
}

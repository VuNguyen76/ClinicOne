package com.clinicone.examination;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorMedicalRecordResponse(
        UUID id,
        String appointmentCode,
        String patientName,
        String specialty,
        LocalDate appointmentDate,
        String reason,
        String examinationNotes,
        String diagnosis,
        String conclusion,
        String treatmentPlan,
        String prescription,
        List<PrescriptionLineResponse> prescriptionLines,
        LocalDate followUpDate,
        Integer followUpDays,
        String followUpNote,
        Instant signedAt,
        String bloodPressure,
        Integer heartRate,
        BigDecimal temperature,
        Integer spO2,
        BigDecimal weight,
        BigDecimal height,
        String allergySummary
) {
    static DoctorMedicalRecordResponse fromSummary(MedicalRecord record) {
        return from(record, false);
    }

    static DoctorMedicalRecordResponse from(MedicalRecord record) {
        return from(record, true);
    }

    private static DoctorMedicalRecordResponse from(MedicalRecord record, boolean includePrescriptionLines) {
        var appointment = record.getSession() == null ? null : record.getSession().getAppointment();
        var profile = appointment == null ? null : appointment.getPatientProfile();
        String patientName = profile != null ? profile.getFullName()
                : appointment == null || appointment.getPatient() == null ? null : appointment.getPatient().getFullName();
        return new DoctorMedicalRecordResponse(record.getId(),
                appointment == null ? null : appointment.getAppointmentCode(), patientName,
                appointment == null ? null : appointment.getSpecialty(),
                appointment == null ? null : appointment.getAppointmentDate(), record.getReason(),
                record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(), record.getTreatmentPlan(),
                record.getPrescription(), includePrescriptionLines
                        ? record.getPrescriptionLines().stream().map(PrescriptionLineResponse::from).toList()
                        : List.of(),
                record.getFollowUpDate(), record.getFollowUpDays(), record.getFollowUpNote(), record.getSignedAt(),
                record.getBloodPressure(), record.getHeartRate(), record.getTemperature(), record.getSpO2(),
                record.getWeight(), record.getHeight(), record.getAllergySummary());
    }
}

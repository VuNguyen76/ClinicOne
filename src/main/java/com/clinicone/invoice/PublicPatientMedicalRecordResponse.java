package com.clinicone.invoice;

import com.clinicone.examination.MedicalRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicPatientMedicalRecordResponse(UUID id, Instant signedAt, String doctorName,
                                                String specialty, String diagnosis, String prescription,
                                                List<PublicPatientPrescriptionResponse> prescriptionLines) {
    public static PublicPatientMedicalRecordResponse from(MedicalRecord record) {
        var appointment = record.getSession() == null ? null : record.getSession().getAppointment();
        return new PublicPatientMedicalRecordResponse(record.getId(), record.getSignedAt(),
                record.getDoctorName(), appointment == null ? null : appointment.getSpecialty(),
                record.getDiagnosis(), record.getPrescription(),
                record.getPrescriptionLines().stream().map(PublicPatientPrescriptionResponse::from).toList());
    }
}

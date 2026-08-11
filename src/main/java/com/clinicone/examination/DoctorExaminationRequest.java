package com.clinicone.examination;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

public record DoctorExaminationRequest(
        @Size(max = 500) String reason,
        @Size(max = 4000) String examinationNotes,
        @Size(max = 1000) String diagnosis,
        @Size(max = 2000) String conclusion,
        @Size(max = 2000) String treatmentPlan,
        @Size(max = 4000) String prescription,
        LocalDate followUpDate,
        Long recordVersion,
        @Size(max = 20) List<@Valid PrescriptionLineRequest> prescriptionLines
) {
    public DoctorExaminationRequest(String reason, String examinationNotes, String diagnosis, String conclusion,
                                    String treatmentPlan, String prescription, LocalDate followUpDate,
                                    Long recordVersion) {
        this(reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                recordVersion, List.of());
    }
}

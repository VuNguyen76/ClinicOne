package com.clinicone.examination;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DoctorExaminationRequest(
        @Size(max = 500) String reason,
        @Size(max = 4000) String examinationNotes,
        @Size(max = 1000) String diagnosis,
        @Size(max = 2000) String conclusion,
        @Size(max = 2000) String treatmentPlan,
        @Size(max = 4000) String prescription,
        LocalDate followUpDate
) {
}

package com.clinicone.examination;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DoctorExaminationRequest(
        @Size(max = 2000) String reason,
        @Size(max = 2000) String examinationNotes,
        @Size(max = 2000) String diagnosis,
        @Size(max = 2000) String conclusion,
        @Size(max = 2000) String treatmentPlan,
        @Size(max = 4000) String prescription,
        LocalDate followUpDate,
        Long recordVersion,
        @Size(max = 20) List<@Valid PrescriptionLineRequest> prescriptionLines,
        @Min(1) @Max(365) Integer followUpDays,
        @Size(max = 500) String followUpNote,
        @Size(max = 30) String bloodPressure,
        @Min(20) @Max(300) Integer heartRate,
        BigDecimal temperature,
        @Min(50) @Max(100) Integer spO2,
        BigDecimal weight,
        BigDecimal height,
        @Size(max = 500) String allergySummary
) {
    public DoctorExaminationRequest(String reason, String examinationNotes, String diagnosis, String conclusion,
                                    String treatmentPlan, String prescription, LocalDate followUpDate,
                                    Long recordVersion) {
        this(reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                recordVersion, List.of(), null, null, null, null, null, null, null, null, null);
    }

    public DoctorExaminationRequest(String reason, String examinationNotes, String diagnosis, String conclusion,
                                    String treatmentPlan, String prescription, LocalDate followUpDate,
                                    Long recordVersion, List<PrescriptionLineRequest> prescriptionLines) {
        this(reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                recordVersion, prescriptionLines, null, null, null, null, null, null, null, null, null);
    }

    public DoctorExaminationRequest(String reason, String examinationNotes, String diagnosis, String conclusion,
                                    String treatmentPlan, String prescription, LocalDate followUpDate,
                                    Long recordVersion, List<PrescriptionLineRequest> prescriptionLines,
                                    Integer followUpDays, String followUpNote) {
        this(reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                recordVersion, prescriptionLines, followUpDays, followUpNote, null, null, null, null, null, null, null);
    }
}

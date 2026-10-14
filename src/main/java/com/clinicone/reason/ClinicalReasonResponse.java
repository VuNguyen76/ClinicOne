package com.clinicone.reason;

import java.util.UUID;

public record ClinicalReasonResponse(
        UUID id,
        String specialtyCode,
        String specialtyName,
        String symptomName,
        int sortOrder,
        boolean active
) {
    public static ClinicalReasonResponse from(ClinicalReason reason) {
        return new ClinicalReasonResponse(
                reason.getId(),
                reason.getSpecialtyCode(),
                reason.getSpecialtyName(),
                reason.getSymptomName(),
                reason.getSortOrder(),
                reason.isActive()
        );
    }
}

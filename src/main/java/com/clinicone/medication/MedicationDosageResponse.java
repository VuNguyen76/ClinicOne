package com.clinicone.medication;

import java.util.UUID;

public record MedicationDosageResponse(
        UUID id,
        String code,
        String unitName,
        String dosageFormat,
        String description,
        boolean active,
        int sortOrder
) {
    public static MedicationDosageResponse from(MedicationDosage entity) {
        return new MedicationDosageResponse(
                entity.getId(),
                entity.getCode(),
                entity.getUnitName(),
                entity.getDosageFormat(),
                entity.getDescription(),
                entity.isActive(),
                entity.getSortOrder()
        );
    }
}

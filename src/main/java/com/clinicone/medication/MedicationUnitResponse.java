package com.clinicone.medication;

import java.util.UUID;

public record MedicationUnitResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        int sortOrder
) {
    public static MedicationUnitResponse from(MedicationUnit entity) {
        return new MedicationUnitResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getSortOrder()
        );
    }
}

package com.clinicone.medication;

import java.util.UUID;

public record MedicationResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        String category,
        String specialties,
        String defaultDosage,
        String defaultInstructions,
        String unit
) {
    public MedicationResponse(UUID id, String code, String name, boolean active) {
        this(id, code, name, active, null, null, null, null, null);
    }

    static MedicationResponse from(Medication medication) {
        return new MedicationResponse(
                medication.getId(),
                medication.getCode(),
                medication.getName(),
                medication.isActive(),
                medication.getCategory(),
                medication.getSpecialties(),
                medication.getDefaultDosage(),
                medication.getDefaultInstructions(),
                medication.getUnit()
        );
    }
}

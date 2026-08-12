package com.clinicone.medication;

import java.util.UUID;

public record MedicationResponse(UUID id, String code, String name, boolean active) {
    static MedicationResponse from(Medication medication) {
        return new MedicationResponse(medication.getId(), medication.getCode(), medication.getName(), medication.isActive());
    }
}

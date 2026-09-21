package com.clinicone.examination;

import java.util.UUID;

public record PrescriptionLineResponse(
        UUID medicationId,
        String medicationName,
        String dosage,
        int quantity,
        String unit,
        String instructions
) {
    public PrescriptionLineResponse(UUID medicationId, String medicationName, String dosage, int quantity, String instructions) {
        this(medicationId, medicationName, dosage, quantity, null, instructions);
    }

    static PrescriptionLineResponse from(PrescriptionLine line) {
        return new PrescriptionLineResponse(line.getSourceMedicationId(), line.getMedicationName(), line.getDosage(),
                line.getQuantity(), line.getUnit(), line.getInstructions());
    }
}

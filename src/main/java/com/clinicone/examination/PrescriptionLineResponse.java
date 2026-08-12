package com.clinicone.examination;

import java.util.UUID;

public record PrescriptionLineResponse(
        UUID medicationId,
        String medicationName,
        String dosage,
        int quantity,
        String instructions
) {
    static PrescriptionLineResponse from(PrescriptionLine line) {
        return new PrescriptionLineResponse(line.getSourceMedicationId(), line.getMedicationName(), line.getDosage(),
                line.getQuantity(), line.getInstructions());
    }
}

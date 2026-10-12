package com.clinicone.invoice;

import com.clinicone.examination.PrescriptionLine;

public record PublicPatientPrescriptionResponse(String medicationName, String dosage,
                                                int quantity, String unit, String instructions) {
    public static PublicPatientPrescriptionResponse from(PrescriptionLine line) {
        return new PublicPatientPrescriptionResponse(line.getMedicationName(), line.getDosage(),
                line.getQuantity(), line.getUnit(), line.getInstructions());
    }
}

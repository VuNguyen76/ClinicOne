package com.clinicone.examination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "prescription_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uk_prescription_lines_record_order", columnNames = {"medical_record_id", "line_number"})
})
public class PrescriptionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(name = "source_medication_id")
    private UUID sourceMedicationId;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Column(nullable = false, length = 100)
    private String dosage;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 500)
    private String instructions;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    protected PrescriptionLine() {
    }

    private PrescriptionLine(MedicalRecord medicalRecord, UUID sourceMedicationId, String medicationName, String dosage,
                             int quantity, String instructions, int lineNumber) {
        this.medicalRecord = medicalRecord;
        this.sourceMedicationId = sourceMedicationId;
        this.medicationName = medicationName;
        this.dosage = dosage;
        this.quantity = quantity;
        this.instructions = instructions;
        this.lineNumber = lineNumber;
    }

    static PrescriptionLine create(MedicalRecord medicalRecord, PrescriptionLineRequest request, int lineNumber) {
        return new PrescriptionLine(medicalRecord, request.medicationId(), request.medicationName().trim(),
                request.dosage().trim(), request.quantity(), request.instructions().trim(), lineNumber);
    }

    static PrescriptionLine create(MedicalRecord medicalRecord, UUID medicationId, String medicationName, String dosage,
                                   int quantity, String instructions, int lineNumber) {
        return new PrescriptionLine(medicalRecord, medicationId, medicationName.trim(), dosage.trim(), quantity,
                instructions.trim(), lineNumber);
    }

    public UUID getSourceMedicationId() { return sourceMedicationId; }
    public String getMedicationName() { return medicationName; }
    public String getDosage() { return dosage; }
    public int getQuantity() { return quantity; }
    public String getInstructions() { return instructions; }
}

package com.clinicone.reason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "clinical_reasons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clinical_reasons_specialty_symptom", columnNames = {"specialty_name", "symptom_name"})
})
public class ClinicalReason {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "specialty_code", nullable = false, length = 20)
    private String specialtyCode;

    @Column(name = "specialty_name", nullable = false, length = 120)
    private String specialtyName;

    @Column(name = "symptom_name", nullable = false, length = 200)
    private String symptomName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicalReason() {
    }

    public ClinicalReason(String specialtyCode, String specialtyName, String symptomName, int sortOrder) {
        this.specialtyCode = specialtyCode.trim();
        this.specialtyName = specialtyName.trim();
        this.symptomName = symptomName.trim();
        this.sortOrder = sortOrder;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void update(String specialtyCode, String specialtyName, String symptomName, int sortOrder) {
        this.specialtyCode = specialtyCode.trim();
        this.specialtyName = specialtyName.trim();
        this.symptomName = symptomName.trim();
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}

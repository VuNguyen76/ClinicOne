package com.clinicone.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "medication_dosages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medication_dosage_code", columnNames = "code")
})
public class MedicationDosage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "unit_name", nullable = false, length = 100)
    private String unitName;

    @Column(name = "dosage_format", nullable = false, length = 200)
    private String dosageFormat;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicationDosage() {
    }

    public MedicationDosage(String code, String unitName, String dosageFormat, String description, int sortOrder) {
        this.code = code;
        this.unitName = unitName;
        this.dosageFormat = dosageFormat;
        this.description = description == null ? "" : description;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public void update(String code, String unitName, String dosageFormat, String description, int sortOrder) {
        this.code = code;
        this.unitName = unitName;
        this.dosageFormat = dosageFormat;
        this.description = description == null ? "" : description;
        this.sortOrder = sortOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

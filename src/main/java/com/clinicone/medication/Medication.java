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
@Table(name = "medication_catalog", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medication_catalog_code", columnNames = "code")
})
public class Medication {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 100)
    private String category;

    @Column(length = 255)
    private String specialties;

    @Column(name = "default_dosage", length = 150)
    private String defaultDosage;

    @Column(name = "default_instructions", length = 500)
    private String defaultInstructions;

    @Column(length = 50)
    private String unit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Medication() {
    }

    private Medication(String code, String name, String category, String specialties,
                       String defaultDosage, String defaultInstructions, String unit) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.specialties = specialties;
        this.defaultDosage = defaultDosage;
        this.defaultInstructions = defaultInstructions;
        this.unit = unit;
        this.active = true;
    }

    public static Medication create(String code, String name) {
        return new Medication(code, name, null, null, null, null, null);
    }

    public static Medication create(String code, String name, String category, String specialties,
                                   String defaultDosage, String defaultInstructions, String unit) {
        return new Medication(code, name, category, specialties, defaultDosage, defaultInstructions, unit);
    }

    public void update(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name, String category, String specialties,
                       String defaultDosage, String defaultInstructions, String unit) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.specialties = specialties;
        this.defaultDosage = defaultDosage;
        this.defaultInstructions = defaultInstructions;
        this.unit = unit;
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

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
@Table(name = "medication_units", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medication_unit_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_medication_unit_name", columnNames = "name")
})
public class MedicationUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

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

    protected MedicationUnit() {
    }

    public MedicationUnit(String code, String name, String description, int sortOrder) {
        this.code = code;
        this.name = name;
        this.description = description == null ? "" : description;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public void update(String code, String name, String description, int sortOrder) {
        this.code = code;
        this.name = name;
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

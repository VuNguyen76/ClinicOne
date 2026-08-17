package com.clinicone.schedule;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "specialties", uniqueConstraints = {
        @UniqueConstraint(name = "uk_specialty_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_specialty_name", columnNames = "name")
})
public class SpecialtyCatalogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpecialtyCatalogEntry() {
    }

    private SpecialtyCatalogEntry(String code, String name, String description) {
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        this.active = true;
    }

    public static SpecialtyCatalogEntry create(String code, String name, String description) {
        return new SpecialtyCatalogEntry(code, name, description);
    }

    public void update(String code, String name, String description) {
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
    }

    public void setActive(boolean active) { this.active = active; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

}

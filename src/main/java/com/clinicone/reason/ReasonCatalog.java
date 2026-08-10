package com.clinicone.reason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reason_catalog", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reason_catalog_type_code", columnNames = {"reason_type", "code"})
})
public class ReasonCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 40)
    private ReasonCatalogType type;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReasonCatalog() {
    }

    private ReasonCatalog(ReasonCatalogType type, String code, String label) {
        this.type = type;
        this.code = code;
        this.label = label;
        this.active = true;
    }

    public static ReasonCatalog create(ReasonCatalogType type, String code, String label) {
        return new ReasonCatalog(type, code, label);
    }

    public void update(String code, String label) {
        this.code = code;
        this.label = label;
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

    public UUID getId() { return id; }
    public ReasonCatalogType getType() { return type; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

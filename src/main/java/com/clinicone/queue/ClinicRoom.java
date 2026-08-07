package com.clinicone.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_rooms")
public class ClinicRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(nullable = false)
    private boolean active;

    /** Opaque, stable value embedded in the QR card displayed outside the room. */
    @Column(name = "qr_token", unique = true, length = 64)
    private String qrToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClinicRoom() {
    }

    private ClinicRoom(String code, String name, String specialty) {
        this.code = code.trim();
        this.name = name.trim();
        this.specialty = specialty.trim();
        this.active = true;
    }

    public static ClinicRoom create(String code, String name, String specialty) {
        return new ClinicRoom(code, name, specialty);
    }

    public void update(String code, String name, String specialty) {
        this.code = code.trim();
        this.name = name.trim();
        this.specialty = specialty.trim();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        ensureQrToken();
    }

    public String ensureQrToken() {
        if (qrToken == null || qrToken.isBlank()) {
            qrToken = UUID.randomUUID().toString();
        }
        return qrToken;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public boolean isActive() { return active; }
    public String getQrToken() { return qrToken; }
}

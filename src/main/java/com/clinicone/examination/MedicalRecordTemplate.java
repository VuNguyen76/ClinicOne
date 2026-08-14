package com.clinicone.examination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medical_record_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medical_record_template_code", columnNames = "code")
})
public class MedicalRecordTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "clinic_service_id")
    private UUID clinicServiceId;

    @Column(length = 500)
    private String description;

    @Column(name = "field_definition", nullable = false, length = 20000)
    private String fieldDefinition;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MedicalRecordTemplate() {
    }

    private MedicalRecordTemplate(String code, String name, String specialty, UUID clinicServiceId,
                                  String description, String fieldDefinition, String createdBy) {
        this.code = code;
        this.name = name;
        this.specialty = specialty;
        this.clinicServiceId = clinicServiceId;
        this.description = description;
        this.fieldDefinition = fieldDefinition;
        this.createdBy = createdBy;
        this.active = true;
    }

    public static MedicalRecordTemplate create(String code, String name, String specialty, UUID clinicServiceId,
                                               String description, String fieldDefinition, String createdBy) {
        return new MedicalRecordTemplate(code, name, specialty, clinicServiceId, description, fieldDefinition, createdBy);
    }

    public void update(String name, String specialty, UUID clinicServiceId, String description,
                       String fieldDefinition) {
        if (!active) {
            throw new IllegalStateException("Mẫu phiếu đã ngưng sử dụng.");
        }
        this.name = name;
        this.specialty = specialty;
        this.clinicServiceId = clinicServiceId;
        this.description = description;
        this.fieldDefinition = fieldDefinition;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public UUID getClinicServiceId() { return clinicServiceId; }
    public String getDescription() { return description; }
    public String getFieldDefinition() { return fieldDefinition; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}

package com.clinicone.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Getter
@Table(name = "clinic_configuration")
public class ClinicConfiguration {
    public static final UUID DEFAULT_ID = UUID.nameUUIDFromBytes(
            "clinicone-default-configuration".getBytes(StandardCharsets.UTF_8));

    @Id
    private UUID id = DEFAULT_ID;

    @Column(name = "unit_name", nullable = false, length = 160)
    private String unitName;

    @Column(name = "department_name", nullable = false, length = 160)
    private String departmentName;

    @Column(name = "hold_minutes", nullable = false)
    private int holdMinutes;

    @Column(name = "cancellation_threshold_hours", nullable = false)
    private int cancellationThresholdHours;

    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicConfiguration() {
    }

    private ClinicConfiguration(String unitName, String departmentName, int holdMinutes,
                                int cancellationThresholdHours, String actor) {
        this.unitName = unitName;
        this.departmentName = departmentName;
        this.holdMinutes = holdMinutes;
        this.cancellationThresholdHours = cancellationThresholdHours;
        this.updatedBy = actor;
    }

    public static ClinicConfiguration defaults() {
        return new ClinicConfiguration("ClinicOne", "Khám bệnh", 10, 12, "SYSTEM");
    }

    public void update(String unitName, String departmentName, int holdMinutes,
                       int cancellationThresholdHours, String actor) {
        this.unitName = unitName;
        this.departmentName = departmentName;
        this.holdMinutes = holdMinutes;
        this.cancellationThresholdHours = cancellationThresholdHours;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (updatedBy == null || updatedBy.isBlank()) updatedBy = "SYSTEM";
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

}

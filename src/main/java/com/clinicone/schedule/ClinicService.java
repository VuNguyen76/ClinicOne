package com.clinicone.schedule;

import com.clinicone.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "clinic_services", uniqueConstraints = @UniqueConstraint(
        name = "uk_clinic_service_key", columnNames = {"name", "specialty", "visit_type"}))
public class ClinicService {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "visit_type", nullable = false, length = 60)
    private String visitType;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean active;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "clinic_service_doctors",
            joinColumns = @JoinColumn(name = "service_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "doctor_profile_id", nullable = false))
    private Set<DoctorProfile> eligibleDoctors = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicService() {
    }

    private ClinicService(String name, String specialty, String visitType, int durationMinutes,
                           Collection<DoctorProfile> eligibleDoctors) {
        this.name = name.trim();
        this.specialty = specialty.trim();
        this.visitType = visitType.trim();
        this.durationMinutes = durationMinutes;
        this.active = true;
        replaceEligibleDoctors(eligibleDoctors);
    }

    public static ClinicService create(String name, String specialty, String visitType, int durationMinutes,
                                       Collection<DoctorProfile> eligibleDoctors) {
        return new ClinicService(name, specialty, visitType, durationMinutes, eligibleDoctors);
    }

    public void update(String name, String specialty, String visitType, int durationMinutes,
                       Collection<DoctorProfile> eligibleDoctors) {
        this.name = name.trim();
        this.specialty = specialty.trim();
        this.visitType = visitType.trim();
        this.durationMinutes = durationMinutes;
        replaceEligibleDoctors(eligibleDoctors);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void replaceEligibleDoctors(Collection<DoctorProfile> doctors) {
        this.eligibleDoctors.clear();
        if (doctors != null) this.eligibleDoctors.addAll(doctors);
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
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public String getVisitType() { return visitType; }
    public int getDurationMinutes() { return durationMinutes; }
    public boolean isActive() { return active; }
    public Set<DoctorProfile> getEligibleDoctors() { return eligibleDoctors; }
}

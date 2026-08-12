package com.clinicone.rescheduling;

import com.clinicone.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "doctor_time_off")
public class DoctorTimeOff {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorTimeOff() {
    }

    private DoctorTimeOff(DoctorProfile doctorProfile, LocalDate startDate, LocalDate endDate, String reason) {
        this.doctorProfile = doctorProfile;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason.trim();
        this.active = true;
    }

    public static DoctorTimeOff create(DoctorProfile doctorProfile, LocalDate startDate, LocalDate endDate,
                                       String reason) {
        return new DoctorTimeOff(doctorProfile, startDate, endDate, reason);
    }

    public void setActive(boolean active) { this.active = active; }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public DoctorProfile getDoctorProfile() { return doctorProfile; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}

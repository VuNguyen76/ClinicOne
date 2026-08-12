package com.clinicone.doctor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_schedules")
public class DoctorSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorSchedule() {
    }

    private DoctorSchedule(DoctorProfile doctorProfile, DayOfWeek dayOfWeek, LocalTime startTime,
                           LocalTime endTime, int slotDurationMinutes) {
        this.doctorProfile = doctorProfile;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.active = true;
    }

    public static DoctorSchedule create(DoctorProfile profile, DayOfWeek dayOfWeek, LocalTime startTime,
                                         LocalTime endTime, int slotDurationMinutes) {
        return new DoctorSchedule(profile, dayOfWeek, startTime, endTime, slotDurationMinutes);
    }

    public void setActive(boolean active) { this.active = active; }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DoctorProfile getDoctorProfile() { return doctorProfile; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getSlotDurationMinutes() { return slotDurationMinutes; }
    public boolean isActive() { return active; }
}

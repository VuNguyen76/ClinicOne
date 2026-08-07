package com.clinicone.doctor;

import com.clinicone.auth.StaffAccount;
import com.clinicone.queue.ClinicRoom;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doctor_profiles_staff", columnNames = "staff_account_id")
})
public class DoctorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_account_id", nullable = false)
    private StaffAccount staffAccount;

    @Column(nullable = false, length = 120)
    private String specialty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ClinicRoom room;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private DoctorProfile(StaffAccount staffAccount, String specialty, ClinicRoom room) {
        this.staffAccount = staffAccount;
        this.specialty = specialty.trim();
        this.room = room;
        this.active = true;
    }

    protected DoctorProfile() {
    }

    public static DoctorProfile create(StaffAccount staffAccount, String specialty, ClinicRoom room) {
        return new DoctorProfile(staffAccount, specialty, room);
    }

    public void updateAssignment(String specialty, ClinicRoom room) {
        this.specialty = specialty.trim();
        this.room = room;
        this.active = true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public StaffAccount getStaffAccount() { return staffAccount; }
    public String getSpecialty() { return specialty; }
    public ClinicRoom getRoom() { return room; }
    public boolean isActive() { return active; }
}

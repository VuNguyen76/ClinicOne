package com.clinicone.doctor;

import lombok.Getter;

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

@Getter
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

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private DoctorProfile(StaffAccount staffAccount, String specialty, ClinicRoom room, String avatarUrl) {
        this.staffAccount = staffAccount;
        this.specialty = specialty.trim();
        this.room = room;
        this.avatarUrl = avatarUrl;
        this.active = true;
    }

    protected DoctorProfile() {
    }

    public static DoctorProfile create(StaffAccount staffAccount, String specialty, ClinicRoom room) {
        return new DoctorProfile(staffAccount, specialty, room, null);
    }

    public static DoctorProfile create(StaffAccount staffAccount, String specialty, ClinicRoom room, String avatarUrl) {
        return new DoctorProfile(staffAccount, specialty, room, avatarUrl);
    }

    public void updateAssignment(String specialty, ClinicRoom room) {
        this.specialty = specialty.trim();
        this.room = room;
        this.active = true;
    }

    public void updateAssignment(String specialty, ClinicRoom room, String avatarUrl) {
        this.specialty = specialty.trim();
        this.room = room;
        this.avatarUrl = avatarUrl;
        this.active = true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

}

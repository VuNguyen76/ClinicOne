package com.clinicone.schedule;

import com.clinicone.auth.PatientAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * An active, short-lived claim on a slot. Consumed holds are removed in the
 * same transaction as appointment creation; only active holds are persisted.
 */
@Entity
@Table(name = "appointment_holds", indexes = {
        @Index(name = "idx_appointment_holds_expiry", columnList = "expires_at"),
        @Index(name = "idx_appointment_holds_slot", columnList = "specialty,appointment_date,start_time,doctor_staff_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointment_holds_claim", columnNames = "hold_key")
})
public class AppointmentHold {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patient;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "doctor_name", nullable = false, length = 120)
    private String doctorName;

    @Column(name = "doctor_staff_id")
    private UUID doctorStaffId;

    @Column(name = "clinic_service_id")
    private UUID serviceId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "hold_key", nullable = false, length = 180)
    private String holdKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AppointmentHold() {
    }

    private AppointmentHold(PatientAccount patient, String specialty, String doctorName, UUID doctorStaffId,
                             LocalDate appointmentDate, LocalTime startTime, String holdKey, Instant expiresAt) {
        this(patient, specialty, doctorName, doctorStaffId, appointmentDate, startTime, holdKey, expiresAt, null);
    }

    private AppointmentHold(PatientAccount patient, String specialty, String doctorName, UUID doctorStaffId,
                             LocalDate appointmentDate, LocalTime startTime, String holdKey, Instant expiresAt,
                             UUID serviceId) {
        this.patient = patient;
        this.specialty = specialty;
        this.doctorName = doctorName;
        this.doctorStaffId = doctorStaffId;
        this.serviceId = serviceId;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.holdKey = holdKey;
        this.expiresAt = expiresAt;
    }

    public static AppointmentHold create(PatientAccount patient, String specialty, String doctorName,
                                          UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime,
                                          String holdKey, Instant expiresAt) {
        return new AppointmentHold(patient, specialty, doctorName, doctorStaffId, appointmentDate, startTime,
                holdKey, expiresAt);
    }

    public static AppointmentHold create(PatientAccount patient, String specialty, String doctorName,
                                          UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime,
                                          String holdKey, Instant expiresAt, UUID serviceId) {
        return new AppointmentHold(patient, specialty, doctorName, doctorStaffId, appointmentDate, startTime,
                holdKey, expiresAt, serviceId);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public PatientAccount getPatient() { return patient; }
    public String getSpecialty() { return specialty; }
    public String getDoctorName() { return doctorName; }
    public UUID getDoctorStaffId() { return doctorStaffId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public String getHoldKey() { return holdKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}

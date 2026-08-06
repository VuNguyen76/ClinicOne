package com.clinicone.appointment;

import com.clinicone.auth.PatientAccount;
import com.clinicone.patientprofile.PatientProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_patient_date", columnList = "patient_account_id,appointment_date,start_time"),
        @Index(name = "idx_appointments_slot_availability", columnList = "specialty,appointment_date,start_time,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointments_patient_slot", columnNames = {"patient_account_id", "appointment_date", "start_time"})
})
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id")
    private PatientProfile patientProfile;

    @Column(name = "appointment_code", nullable = false, unique = true, length = 24)
    private String appointmentCode;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "doctor_name", nullable = false, length = 120)
    private String doctorName;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Appointment() {
    }

    private Appointment(PatientAccount patient, String appointmentCode, String specialty, String doctorName,
                         LocalDate appointmentDate, LocalTime startTime, String reason) {
        this.patient = patient;
        this.appointmentCode = appointmentCode;
        this.specialty = specialty;
        this.doctorName = doctorName;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.reason = reason;
        this.status = AppointmentStatus.BOOKED;
    }

    public static Appointment create(PatientAccount patient, String appointmentCode, String specialty,
                                      String doctorName, LocalDate appointmentDate, LocalTime startTime,
                                      String reason) {
        return new Appointment(patient, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason);
    }

    public static Appointment create(PatientAccount patient, PatientProfile patientProfile, String appointmentCode,
                                     String specialty, String doctorName, LocalDate appointmentDate,
                                     LocalTime startTime, String reason) {
        Appointment appointment = new Appointment(patient, appointmentCode, specialty, doctorName, appointmentDate,
                startTime, reason);
        appointment.patientProfile = patientProfile;
        return appointment;
    }

    public void cancel(String reason) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public void reschedule(LocalDate appointmentDate, LocalTime startTime) {
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
    }

    static Appointment existing(PatientAccount patient, String appointmentCode, String specialty, String doctorName,
                                 LocalDate appointmentDate, LocalTime startTime, String reason) {
        return create(patient, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public String getAppointmentCode() { return appointmentCode; }
    public String getSpecialty() { return specialty; }
    public String getDoctorName() { return doctorName; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public String getReason() { return reason; }
    public PatientProfile getPatientProfile() { return patientProfile; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
}

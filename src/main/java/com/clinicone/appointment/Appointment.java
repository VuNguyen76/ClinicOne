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
        @Index(name = "idx_appointments_slot_availability", columnList = "specialty,appointment_date,start_time,status"),
        @Index(name = "idx_appointments_doctor_slot", columnList = "doctor_staff_id,appointment_date,start_time,status")
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

    @Column(name = "doctor_staff_id")
    private UUID doctorStaffId;

    @Column(name = "clinic_service_id")
    private UUID serviceId;

    @Column(name = "service_name", length = 120)
    private String serviceName;

    @Column(name = "visit_type", length = 60)
    private String visitType;

    @Column(name = "service_duration_minutes")
    private Integer serviceDurationMinutes;

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
        this(patient, null, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason);
    }

    private Appointment(PatientAccount patient, UUID doctorStaffId, String appointmentCode, String specialty,
                         String doctorName, LocalDate appointmentDate, LocalTime startTime, String reason) {
        this.patient = patient;
        this.doctorStaffId = doctorStaffId;
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

    public static Appointment create(PatientAccount patient, UUID doctorStaffId, String appointmentCode,
                                      String specialty, String doctorName, LocalDate appointmentDate,
                                      LocalTime startTime, String reason) {
        return new Appointment(patient, doctorStaffId, appointmentCode, specialty, doctorName, appointmentDate,
                startTime, reason);
    }

    public static Appointment create(PatientAccount patient, PatientProfile patientProfile, String appointmentCode,
                                     String specialty, String doctorName, LocalDate appointmentDate,
                                     LocalTime startTime, String reason) {
        Appointment appointment = new Appointment(patient, appointmentCode, specialty, doctorName, appointmentDate,
                startTime, reason);
        appointment.patientProfile = patientProfile;
        return appointment;
    }

    public static Appointment create(PatientAccount patient, UUID doctorStaffId, PatientProfile patientProfile,
                                      String appointmentCode, String specialty, String doctorName,
                                      LocalDate appointmentDate, LocalTime startTime, String reason) {
        Appointment appointment = new Appointment(patient, doctorStaffId, appointmentCode, specialty, doctorName,
                appointmentDate, startTime, reason);
        appointment.patientProfile = patientProfile;
        return appointment;
    }

    public void cancel(String reason) {
        cancel(reason, Instant.now());
    }

    public void cancel(String reason, Instant cancelledAt) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = cancelledAt == null ? Instant.now() : cancelledAt;
        this.cancellationReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public void checkIn() {
        if (this.status == AppointmentStatus.CHECKED_IN) {
            return;
        }
        if (this.status != AppointmentStatus.BOOKED) {
            throw new IllegalStateException("Lịch hẹn không còn cho phép check-in.");
        }
        this.status = AppointmentStatus.CHECKED_IN;
    }

    public void markAbsent() {
        if (this.status == AppointmentStatus.ABSENT) {
            return;
        }
        if (this.status != AppointmentStatus.BOOKED) {
            throw new IllegalStateException("Chỉ lịch hẹn chưa check-in mới được ghi nhận vắng mặt.");
        }
        this.status = AppointmentStatus.ABSENT;
    }

    public void markNotPerformed() {
        if (this.status == AppointmentStatus.NOT_PERFORMED) {
            return;
        }
        if (this.status != AppointmentStatus.BOOKED && this.status != AppointmentStatus.CHECKED_IN) {
            throw new IllegalStateException("Lịch hẹn không còn phù hợp để ghi nhận không thực hiện.");
        }
        this.status = AppointmentStatus.NOT_PERFORMED;
    }

    public void complete() {
        if (this.status == AppointmentStatus.COMPLETED) {
            return;
        }
        if (this.status != AppointmentStatus.BOOKED && this.status != AppointmentStatus.CHECKED_IN) {
            throw new IllegalStateException("Chỉ có thể hoàn tất lịch hẹn đang được xử lý");
        }
        this.status = AppointmentStatus.COMPLETED;
    }

    public void reschedule(LocalDate appointmentDate, LocalTime startTime) {
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
    }

    public void reschedule(LocalDate appointmentDate, LocalTime startTime, UUID doctorStaffId, String doctorName) {
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.doctorStaffId = doctorStaffId;
        this.doctorName = doctorName == null || doctorName.isBlank() ? this.doctorName : doctorName.trim();
    }

    public void applyServiceSnapshot(UUID serviceId, String serviceName, String visitType, int durationMinutes) {
        this.serviceId = serviceId;
        this.serviceName = serviceName == null || serviceName.isBlank() ? null : serviceName.trim();
        this.visitType = visitType == null || visitType.isBlank() ? null : visitType.trim();
        this.serviceDurationMinutes = durationMinutes > 0 ? durationMinutes : null;
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
    public PatientAccount getPatient() { return patient; }
    public String getAppointmentCode() { return appointmentCode; }
    public String getSpecialty() { return specialty; }
    public String getDoctorName() { return doctorName; }
    public UUID getDoctorStaffId() { return doctorStaffId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getVisitType() { return visitType; }
    public Integer getServiceDurationMinutes() { return serviceDurationMinutes; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public String getReason() { return reason; }
    public PatientProfile getPatientProfile() { return patientProfile; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
}

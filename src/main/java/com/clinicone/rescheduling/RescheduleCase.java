package com.clinicone.rescheduling;

import com.clinicone.appointment.Appointment;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "appointment_reschedule_cases", indexes = {
        @Index(name = "idx_reschedule_cases_status_created", columnList = "status,created_at"),
        @Index(name = "idx_reschedule_cases_appointment", columnList = "appointment_id,status")
})
public class RescheduleCase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "specialty", nullable = false, length = 120)
    private String specialty;

    @Column(name = "old_doctor_name", nullable = false, length = 120)
    private String oldDoctorName;

    @Column(name = "old_doctor_staff_id")
    private UUID oldDoctorStaffId;

    @Column(name = "old_appointment_date", nullable = false)
    private LocalDate oldAppointmentDate;

    @Column(name = "old_start_time", nullable = false)
    private LocalTime oldStartTime;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RescheduleCaseStatus status;

    @Column(name = "new_doctor_name", length = 120)
    private String newDoctorName;

    @Column(name = "new_doctor_staff_id")
    private UUID newDoctorStaffId;

    @Column(name = "new_appointment_date")
    private LocalDate newAppointmentDate;

    @Column(name = "new_start_time")
    private LocalTime newStartTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected RescheduleCase() {
    }

    private RescheduleCase(Appointment appointment, String reason) {
        this.appointment = appointment;
        this.specialty = appointment.getSpecialty();
        this.oldDoctorName = appointment.getDoctorName();
        this.oldDoctorStaffId = appointment.getDoctorStaffId();
        this.oldAppointmentDate = appointment.getAppointmentDate();
        this.oldStartTime = appointment.getStartTime();
        this.reason = reason.trim();
        this.status = RescheduleCaseStatus.OPEN;
    }

    public static RescheduleCase open(Appointment appointment, String reason) {
        if (appointment == null || appointment.getStatus() != com.clinicone.appointment.AppointmentStatus.BOOKED) {
            throw new IllegalArgumentException("Only booked appointments can be rescheduled");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Reschedule reason is invalid");
        }
        return new RescheduleCase(appointment, reason);
    }

    public void resolve(LocalDate date, LocalTime startTime, UUID doctorStaffId, String doctorName,
                        Instant resolvedAt) {
        if (status != RescheduleCaseStatus.OPEN) {
            throw new IllegalStateException("Lịch cần sắp xếp lại đã được xử lý.");
        }
        this.newAppointmentDate = date;
        this.newStartTime = startTime;
        this.newDoctorStaffId = doctorStaffId;
        this.newDoctorName = doctorName;
        this.resolvedAt = resolvedAt;
        this.status = RescheduleCaseStatus.RESOLVED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public String getSpecialty() { return specialty; }
    public String getOldDoctorName() { return oldDoctorName; }
    public UUID getOldDoctorStaffId() { return oldDoctorStaffId; }
    public LocalDate getOldAppointmentDate() { return oldAppointmentDate; }
    public LocalTime getOldStartTime() { return oldStartTime; }
    public String getReason() { return reason; }
    public RescheduleCaseStatus getStatus() { return status; }
    public String getNewDoctorName() { return newDoctorName; }
    public UUID getNewDoctorStaffId() { return newDoctorStaffId; }
    public LocalDate getNewAppointmentDate() { return newAppointmentDate; }
    public LocalTime getNewStartTime() { return newStartTime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}

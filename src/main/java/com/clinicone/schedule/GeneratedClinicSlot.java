package com.clinicone.schedule;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "generated_clinic_slots", uniqueConstraints = @UniqueConstraint(
        name = "uk_generated_slot_template_time",
        columnNames = {"template_id", "appointment_date", "start_time"}))
public class GeneratedClinicSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkScheduleTemplate template;

    @Column(name = "clinic_service_id", nullable = false)
    private UUID clinicServiceId;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "visit_type", nullable = false, length = 60)
    private String visitType;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "doctor_staff_id", nullable = false)
    private UUID doctorStaffId;

    @Column(name = "doctor_name", nullable = false, length = 160)
    private String doctorName;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "room_code", nullable = false, length = 32)
    private String roomCode;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GeneratedSlotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GeneratedClinicSlot() {
    }

    private GeneratedClinicSlot(WorkScheduleTemplate template, LocalDate appointmentDate,
                                LocalTime startTime, LocalTime endTime) {
        this.template = template;
        this.clinicServiceId = template.getClinicService().getId();
        this.specialty = template.getSpecialty();
        this.visitType = template.getVisitType();
        this.durationMinutes = template.getDurationMinutes();
        this.doctorStaffId = template.getDoctorProfile().getStaffAccount().getId();
        this.doctorName = template.getDoctorProfile().getStaffAccount().getFullName();
        this.roomId = template.getRoom().getId();
        this.roomCode = template.getRoom().getCode();
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = GeneratedSlotStatus.OPEN;
    }

    public static GeneratedClinicSlot create(WorkScheduleTemplate template, LocalDate appointmentDate,
                                             LocalTime startTime, LocalTime endTime) {
        return new GeneratedClinicSlot(template, appointmentDate, startTime, endTime);
    }

    public void cancel() { this.status = GeneratedSlotStatus.CANCELLED; }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public WorkScheduleTemplate getTemplate() { return template; }
    public UUID getClinicServiceId() { return clinicServiceId; }
    public String getSpecialty() { return specialty; }
    public String getVisitType() { return visitType; }
    public int getDurationMinutes() { return durationMinutes; }
    public UUID getDoctorStaffId() { return doctorStaffId; }
    public String getDoctorName() { return doctorName; }
    public UUID getRoomId() { return roomId; }
    public String getRoomCode() { return roomCode; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public GeneratedSlotStatus getStatus() { return status; }
}

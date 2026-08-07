package com.clinicone.examination;

import com.clinicone.appointment.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "examination_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_examination_sessions_appointment", columnNames = "appointment_id")
})
public class ExaminationSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExaminationSessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExaminationSession() {
    }

    private ExaminationSession(Appointment appointment) {
        this.appointment = appointment;
        this.status = ExaminationSessionStatus.SCHEDULED;
    }

    public static ExaminationSession create(Appointment appointment) {
        return new ExaminationSession(appointment);
    }

    public void begin() {
        if (status == ExaminationSessionStatus.COMPLETED) {
            throw new IllegalStateException("Phiên khám đã hoàn tất.");
        }
        status = ExaminationSessionStatus.IN_PROGRESS;
    }

    public void complete() {
        status = ExaminationSessionStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public ExaminationSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

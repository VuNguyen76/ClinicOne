package com.clinicone.examination;

import lombok.Getter;

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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "examination_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_examination_sessions_appointment", columnNames = "appointment_id"),
        @UniqueConstraint(name = "uk_examination_sessions_start_key", columnNames = "start_request_key")
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

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_request_key", length = 80)
    private String startRequestKey;

    @Column(name = "sign_request_key", length = 80)
    private String signRequestKey;

    @Version
    @Column(nullable = false)
    private long version;

    protected ExaminationSession() {
    }

    private ExaminationSession(Appointment appointment) {
        this.appointment = appointment;
        this.status = ExaminationSessionStatus.SCHEDULED;
    }

    public static ExaminationSession create(Appointment appointment) {
        return new ExaminationSession(appointment);
    }

    public void checkIn() {
        if (status == ExaminationSessionStatus.COMPLETED
                || status == ExaminationSessionStatus.CANCELLED) {
            throw new IllegalStateException("Phiên khám không còn cho phép check-in.");
        }
        // Check-in creates the session; the session remains "Đã tạo" until the
        // doctor explicitly starts the examination.
    }

    public void begin() {
        if (status == ExaminationSessionStatus.COMPLETED
                || status == ExaminationSessionStatus.CANCELLED) {
            throw new IllegalStateException("Phiên khám đã hoàn tất.");
        }
        status = ExaminationSessionStatus.IN_PROGRESS;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void assignStartRequestKey(String requestKey) {
        if ((startRequestKey == null || startRequestKey.isBlank())
                && requestKey != null && !requestKey.isBlank()) {
            startRequestKey = requestKey.trim();
        }
    }

    public void assignSignRequestKey(String requestKey) {
        if ((signRequestKey == null || signRequestKey.isBlank())
                && requestKey != null && !requestKey.isBlank()) {
            signRequestKey = requestKey.trim();
        }
    }

    public void complete() {
        status = ExaminationSessionStatus.COMPLETED;
        if (endedAt == null) {
            endedAt = Instant.now();
        }
    }

    public void stop() {
        if (status != ExaminationSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ có thể dừng lượt đang khám.");
        }
        status = ExaminationSessionStatus.CANCELLED;
    }

    public void resetForWrongProfile() {
        if (status != ExaminationSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ có thể xử lý nhầm hồ sơ khi lượt đang khám.");
        }
        status = ExaminationSessionStatus.SCHEDULED;
        startedAt = null;
        endedAt = null;
        startRequestKey = null;
        signRequestKey = null;
    }

    public void cancel() {
        if (status == ExaminationSessionStatus.CANCELLED) {
            return;
        }
        if (status == ExaminationSessionStatus.IN_PROGRESS || status == ExaminationSessionStatus.COMPLETED) {
            throw new IllegalStateException("Lượt khám đã bắt đầu, không thể ghi nhận rời trước khám.");
        }
        status = ExaminationSessionStatus.CANCELLED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

}

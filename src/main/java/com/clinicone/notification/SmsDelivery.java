package com.clinicone.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sms_deliveries", indexes = {
        @Index(name = "idx_sms_deliveries_due", columnList = "status,available_at"),
        @Index(name = "idx_sms_deliveries_patient", columnList = "patient_account_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_sms_deliveries_event", columnNames = "event_key")
})
public class SmsDelivery {
    static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_account_id", nullable = false)
    private UUID patientAccountId;

    @Column(name = "event_key", nullable = false, length = 160)
    private String eventKey;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmsDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SmsDelivery() {
    }

    private SmsDelivery(UUID patientAccountId, String eventKey, String phone, String message, Instant now) {
        this.patientAccountId = patientAccountId;
        this.eventKey = eventKey;
        this.phone = phone;
        this.message = message;
        this.status = SmsDeliveryStatus.PENDING;
        this.attempts = 0;
        this.availableAt = now;
    }

    public static SmsDelivery pending(UUID patientAccountId, String eventKey, String phone, String message,
                                      Instant now) {
        return new SmsDelivery(patientAccountId, eventKey, phone, message, now);
    }

    public void claim(Instant now, Instant lockUntil) {
        if (status != SmsDeliveryStatus.PENDING && status != SmsDeliveryStatus.RETRY_WAITING
                && status != SmsDeliveryStatus.PROCESSING) {
            throw new IllegalStateException("SMS delivery is not claimable");
        }
        status = SmsDeliveryStatus.PROCESSING;
        attempts++;
        lockedUntil = lockUntil;
        lastError = null;
    }

    public void markSent(Instant now) {
        status = SmsDeliveryStatus.SENT;
        sentAt = now;
        lockedUntil = null;
        lastError = null;
    }

    public void markFailed(Instant now, String error) {
        lockedUntil = null;
        lastError = normalizeError(error);
        if (attempts >= MAX_ATTEMPTS) {
            status = SmsDeliveryStatus.FAILED;
            availableAt = now;
        } else {
            status = SmsDeliveryStatus.RETRY_WAITING;
            availableAt = now.plusSeconds(5 * 60L);
        }
    }

    private String normalizeError(String error) {
        if (error == null || error.isBlank()) return "SMS provider rejected the request";
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPatientAccountId() { return patientAccountId; }
    public String getEventKey() { return eventKey; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public SmsDeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

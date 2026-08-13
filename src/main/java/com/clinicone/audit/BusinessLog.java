package com.clinicone.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Append-only business journal entry. State changes and their journal entry
 * are persisted in the same transaction by the owning service.
 */
@Entity
@Table(name = "business_logs", indexes = {
        @Index(name = "idx_business_logs_entity", columnList = "entity_type,entity_id,occurred_at"),
        @Index(name = "idx_business_logs_event", columnList = "event_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_logs_event_entity", columnNames = {"event_id", "entity_type", "entity_id"})
})
public class BusinessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "next_status", nullable = false, length = 40)
    private String nextStatus;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "hash", length = 64)
    private String hash;

    protected BusinessLog() {
    }

    private BusinessLog(UUID eventId, String entityType, UUID entityId, String previousStatus,
                        String nextStatus, String eventType, String actor, String reason) {
        this.eventId = eventId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.eventType = eventType;
        this.actor = actor;
        this.reason = reason;
    }

    public static BusinessLog transition(UUID eventId, String entityType, UUID entityId,
                                          String previousStatus, String nextStatus, String eventType,
                                          String actor, String reason) {
        return new BusinessLog(eventId, entityType, entityId, previousStatus, nextStatus, eventType, actor, reason);
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        }
        if (hash == null) {
            hash = calculateHash(previousHash);
        }
    }

    void seal(String previousHash) {
        this.previousHash = previousHash;
        this.occurredAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        this.hash = calculateHash(previousHash);
    }

    boolean isHashValid(String expectedPreviousHash) {
        return hash != null && java.util.Objects.equals(previousHash, expectedPreviousHash)
                && hash.equals(calculateHash(previousHash));
    }

    private String calculateHash(String previousHash) {
        String payload = String.join("|", value(previousHash), value(eventId), value(entityType), value(entityId),
                value(previousStatus), value(nextStatus), value(eventType), value(actor), value(reason),
                value(occurredAt));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNextStatus() { return nextStatus; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
}

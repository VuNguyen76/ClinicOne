package com.clinicone.audit;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "access_audit_events")
public class AccessAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(nullable = false, length = 180)
    private String function;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AccessAuditEvent() { }

    private AccessAuditEvent(String eventType, String actor, String outcome, String function, String ipAddress,
                              Instant occurredAt) {
        this.eventType = eventType;
        this.actor = actor;
        this.outcome = outcome;
        this.function = function;
        this.ipAddress = ipAddress;
        this.occurredAt = occurredAt;
    }

    public static AccessAuditEvent record(String eventType, String actor, String outcome, String function,
                                           String ipAddress, Instant occurredAt) {
        return new AccessAuditEvent(eventType, actor, outcome, function, ipAddress, occurredAt);
    }

    @PrePersist
    void onCreate() { if (occurredAt == null) occurredAt = Instant.now(); }

}

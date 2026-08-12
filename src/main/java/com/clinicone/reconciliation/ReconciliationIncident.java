package com.clinicone.reconciliation;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_incidents", indexes = {
        @Index(name = "idx_reconciliation_entity", columnList = "entity_type,entity_id,status"),
        @Index(name = "idx_reconciliation_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_reconciliation_incident_code", columnNames = "incident_code")
})
public class ReconciliationIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_code", nullable = false, length = 40)
    private String incidentCode;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 120)
    private String assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", length = 40)
    private ReconciliationAction resolutionAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 20)
    private ReconciliationReferenceType referenceType;

    @Column(name = "reference_value", length = 120)
    private String referenceValue;

    @Column(name = "result_note", length = 500)
    private String resultNote;

    @Column(name = "closed_by", length = 120)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationIncident() {
    }

    private ReconciliationIncident(String incidentCode, String entityType, UUID entityId, UUID eventId,
                                    String reason, String assignee) {
        this.incidentCode = incidentCode;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventId = eventId;
        this.reason = reason;
        this.assignee = assignee;
        this.status = ReconciliationStatus.OPEN;
    }

    public static ReconciliationIncident open(String incidentCode, String entityType, UUID entityId, UUID eventId,
                                              String reason, String assignee) {
        return new ReconciliationIncident(incidentCode, entityType, entityId, eventId, reason, assignee);
    }

    public void close(ReconciliationAction action, ReconciliationReferenceType referenceType,
                      String referenceValue, String resultNote, String closer, Instant closedAt) {
        if (status != ReconciliationStatus.OPEN) {
            throw new IllegalStateException("Reconciliation is already closed");
        }
        this.status = ReconciliationStatus.CLOSED;
        this.resolutionAction = action;
        this.referenceType = referenceType;
        this.referenceValue = referenceValue;
        this.resultNote = resultNote;
        this.closedBy = closer;
        this.closedAt = closedAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIncidentCode() { return incidentCode; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public UUID getEventId() { return eventId; }
    public String getReason() { return reason; }
    public String getAssignee() { return assignee; }
    public ReconciliationStatus getStatus() { return status; }
    public ReconciliationAction getResolutionAction() { return resolutionAction; }
    public ReconciliationReferenceType getReferenceType() { return referenceType; }
    public String getReferenceValue() { return referenceValue; }
    public String getResultNote() { return resultNote; }
    public String getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

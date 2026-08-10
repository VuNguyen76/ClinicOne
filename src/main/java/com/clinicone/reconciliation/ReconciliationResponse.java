package com.clinicone.reconciliation;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationResponse(UUID id, String incidentCode, String entityType, UUID entityId,
                                     UUID eventId, String reason, String assignee, ReconciliationStatus status,
                                     ReconciliationAction resolutionAction,
                                     ReconciliationReferenceType referenceType, String referenceValue,
                                     String resultNote, String closedBy, Instant closedAt, Instant createdAt) {
    static ReconciliationResponse from(ReconciliationIncident incident) {
        return new ReconciliationResponse(incident.getId(), incident.getIncidentCode(), incident.getEntityType(),
                incident.getEntityId(), incident.getEventId(), incident.getReason(), incident.getAssignee(),
                incident.getStatus(), incident.getResolutionAction(), incident.getReferenceType(),
                incident.getReferenceValue(), incident.getResultNote(), incident.getClosedBy(), incident.getClosedAt(),
                incident.getCreatedAt());
    }
}

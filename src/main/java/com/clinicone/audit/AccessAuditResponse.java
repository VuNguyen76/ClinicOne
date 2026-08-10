package com.clinicone.audit;

import java.time.Instant;
import java.util.UUID;

public record AccessAuditResponse(UUID id, String eventType, String actor, String outcome, String function,
                                  String ipAddress, Instant occurredAt) {
    static AccessAuditResponse from(AccessAuditEvent event) {
        return new AccessAuditResponse(event.getId(), event.getEventType(), event.getActor(), event.getOutcome(),
                event.getFunction(), event.getIpAddress(), event.getOccurredAt());
    }
}

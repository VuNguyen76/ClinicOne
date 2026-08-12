package com.clinicone.audit;

import java.time.Instant;
import java.util.UUID;

public record BusinessLogResponse(
        UUID id,
        UUID eventId,
        String entityType,
        UUID entityId,
        String previousStatus,
        String nextStatus,
        String eventType,
        String actor,
        String reason,
        Instant occurredAt
) {
    static BusinessLogResponse from(BusinessLog log) {
        return new BusinessLogResponse(log.getId(), log.getEventId(), log.getEntityType(), log.getEntityId(),
                log.getPreviousStatus(), log.getNextStatus(), log.getEventType(), log.getActor(), log.getReason(),
                log.getOccurredAt());
    }
}

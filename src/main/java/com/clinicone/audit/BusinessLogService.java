package com.clinicone.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BusinessLogService {
    private final BusinessLogRepository repository;

    public BusinessLogService(BusinessLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a real state transition. No entry is created when the state did
     * not change, which keeps retries idempotent and the journal meaningful.
     */
    @Transactional
    public void recordTransition(UUID eventId, String entityType, UUID entityId, String previousStatus,
                                 String nextStatus, String eventType, String actor, String reason) {
        if (eventId == null || entityType == null || entityId == null || nextStatus == null || eventType == null) {
            throw new IllegalArgumentException("Business log requires event, entity, next state and event type");
        }
        if (sameState(previousStatus, nextStatus)) {
            return;
        }
        String normalizedReason = normalizeReason(reason);
        if (repository.existsByEventIdAndEntityTypeAndEntityId(eventId, entityType, entityId)) {
            return;
        }
        repository.save(BusinessLog.transition(eventId, normalize(entityType, 40), entityId,
                normalizeNullable(previousStatus, 40), normalize(nextStatus, 40), normalize(eventType, 80),
                normalizeActor(actor), normalizedReason));
    }

    @Transactional(readOnly = true)
    public List<BusinessLogResponse> list(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(entityType, entityId).stream()
                .map(BusinessLogResponse::from)
                .toList();
    }

    private boolean sameState(String previousStatus, String nextStatus) {
        return previousStatus != null && previousStatus.equals(nextStatus);
    }

    private String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "SYSTEM" : normalize(actor, 120);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        return normalize(reason, 500);
    }

    private String normalizeNullable(String value, int maxLength) {
        return value == null || value.isBlank() ? null : normalize(value, maxLength);
    }

    private String normalize(String value, int maxLength) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Business log field is invalid");
        }
        return normalized;
    }
}

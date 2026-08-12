package com.clinicone.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AccessAuditService {
    private final AccessAuditRepository repository;
    private final Clock clock;

    public AccessAuditService(AccessAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AccessAuditResponse record(String eventType, String actor, String outcome, String function,
                                      String ipAddress) {
        AccessAuditEvent event = AccessAuditEvent.record(normalize(eventType, 40), normalize(actor, 120),
                normalize(outcome, 20), normalize(function, 180), normalizeNullable(ipAddress, 64),
                Instant.now(clock));
        return AccessAuditResponse.from(repository.save(event));
    }

    @Transactional(readOnly = true)
    public List<AccessAuditResponse> list(Instant from, Instant to, String actor, String outcome, String eventType) {
        return repository.findFiltered(from, to, normalizeNullable(actor, 120), normalizeNullable(outcome, 20),
                        normalizeNullable(eventType, 40)).stream()
                .limit(500)
                .map(AccessAuditResponse::from)
                .toList();
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException("Access audit field is invalid");
        }
        return value.trim();
    }

    private String normalizeNullable(String value, int maxLength) {
        return value == null || value.isBlank() ? null : normalize(value, maxLength);
    }
}

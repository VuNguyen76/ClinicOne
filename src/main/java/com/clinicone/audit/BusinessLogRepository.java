package com.clinicone.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessLogRepository extends JpaRepository<BusinessLog, UUID> {
    List<BusinessLog> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String entityType, UUID entityId);

    boolean existsByEventIdAndEntityTypeAndEntityId(UUID eventId, String entityType, UUID entityId);

    List<BusinessLog> findAllByOrderByOccurredAtAscIdAsc();
}

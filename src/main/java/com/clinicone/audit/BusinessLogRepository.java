package com.clinicone.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BusinessLogRepository extends JpaRepository<BusinessLog, UUID> {
    List<BusinessLog> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String entityType, UUID entityId);

    Page<BusinessLog> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String entityType, UUID entityId,
                                                                            Pageable pageable);

    Page<BusinessLog> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(String entityType, UUID entityId,
                                                                            Pageable pageable);

    Page<BusinessLog> findByEntityIdOrderByOccurredAtDescIdDesc(UUID entityId, Pageable pageable);

    Page<BusinessLog> findByEntityTypeOrderByOccurredAtDescIdDesc(String entityType, Pageable pageable);

    Page<BusinessLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    boolean existsByEventIdAndEntityTypeAndEntityId(UUID eventId, String entityType, UUID entityId);

    List<BusinessLog> findAllByOrderByOccurredAtAscIdAsc();

    java.util.Optional<BusinessLog> findTopByOrderByOccurredAtDescIdDesc();
}

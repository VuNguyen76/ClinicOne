package com.clinicone.reconciliation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationIncidentRepository extends JpaRepository<ReconciliationIncident, UUID> {
    List<ReconciliationIncident> findAllByOrderByCreatedAtDesc();

    List<ReconciliationIncident> findByStatusOrderByCreatedAtDesc(ReconciliationStatus status);

    boolean existsByEntityTypeAndEntityIdAndStatus(String entityType, UUID entityId, ReconciliationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReconciliationIncident r where r.id = :id")
    Optional<ReconciliationIncident> findByIdForUpdate(@Param("id") UUID id);
}

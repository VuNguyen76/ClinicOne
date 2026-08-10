package com.clinicone.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccessAuditRepository extends JpaRepository<AccessAuditEvent, UUID> {
    @Query("""
            select event from AccessAuditEvent event
            where (:fromTime is null or event.occurredAt >= :fromTime)
              and (:toTime is null or event.occurredAt < :toTime)
              and (:actor is null or lower(event.actor) like lower(concat('%', :actor, '%')))
              and (:outcome is null or event.outcome = :outcome)
              and (:eventType is null or event.eventType = :eventType)
            order by event.occurredAt desc, event.id desc
            """)
    List<AccessAuditEvent> findFiltered(@Param("fromTime") Instant fromTime,
                                        @Param("toTime") Instant toTime,
                                        @Param("actor") String actor,
                                        @Param("outcome") String outcome,
                                        @Param("eventType") String eventType);
}

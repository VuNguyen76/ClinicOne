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
            where event.occurredAt >= :fromTime
              and event.occurredAt < :toTime
              and (:actor = '' or lower(event.actor) like concat('%', lower(:actor), '%'))
              and (:outcome = '' or event.outcome = :outcome)
              and (:eventType = '' or event.eventType = :eventType)
            order by event.occurredAt desc, event.id desc
            """)
    List<AccessAuditEvent> findFiltered(@Param("fromTime") Instant fromTime,
                                        @Param("toTime") Instant toTime,
                                        @Param("actor") String actor,
                                        @Param("outcome") String outcome,
                                        @Param("eventType") String eventType);
}

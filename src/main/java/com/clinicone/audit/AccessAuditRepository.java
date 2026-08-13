package com.clinicone.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccessAuditRepository extends JpaRepository<AccessAuditEvent, UUID> {
    @Query(value = """
            select * from access_audit_events
            where occurred_at >= :fromTime
              and occurred_at < :toTime
              and (cast(:actor as text) = ''
                   or lower(actor) like lower(concat('%', cast(:actor as text), '%')))
              and (cast(:outcome as text) = '' or outcome = cast(:outcome as text))
              and (cast(:eventType as text) = '' or event_type = cast(:eventType as text))
            order by occurred_at desc, id desc
            """, nativeQuery = true)
    List<AccessAuditEvent> findFiltered(@Param("fromTime") Instant fromTime,
                                        @Param("toTime") Instant toTime,
                                        @Param("actor") String actor,
                                        @Param("outcome") String outcome,
                                        @Param("eventType") String eventType);
}

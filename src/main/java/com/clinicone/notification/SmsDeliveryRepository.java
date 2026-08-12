package com.clinicone.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsDeliveryRepository extends JpaRepository<SmsDelivery, UUID> {
    Optional<SmsDelivery> findByEventKey(String eventKey);

    @Query("""
            select d from SmsDelivery d
            where (d.status in (com.clinicone.notification.SmsDeliveryStatus.PENDING,
                                com.clinicone.notification.SmsDeliveryStatus.RETRY_WAITING)
                   and d.availableAt <= :now)
               or (d.status = com.clinicone.notification.SmsDeliveryStatus.PROCESSING
                   and d.lockedUntil <= :now)
            order by d.availableAt asc, d.createdAt asc
            """)
    List<SmsDelivery> findDue(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SmsDelivery d where d.id = :id")
    Optional<SmsDelivery> findByIdForUpdate(@Param("id") UUID id);

    List<SmsDelivery> findTop100ByOrderByCreatedAtDesc();
}

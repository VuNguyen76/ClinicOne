package com.clinicone.rescheduling;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RescheduleCaseRepository extends JpaRepository<RescheduleCase, UUID> {
    List<RescheduleCase> findByStatusOrderByCreatedAtAsc(RescheduleCaseStatus status);

    Optional<RescheduleCase> findByAppointmentIdAndStatus(UUID appointmentId, RescheduleCaseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from RescheduleCase item where item.id = :id")
    Optional<RescheduleCase> findByIdForUpdate(@Param("id") UUID id);
}

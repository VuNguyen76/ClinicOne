package com.clinicone.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ClinicRoomRepository extends JpaRepository<ClinicRoom, UUID> {
    Optional<ClinicRoom> findByCodeAndActiveTrue(String code);

    /**
     * Serializes queue-number allocation for a room. The row lock is held by
     * the surrounding service transaction until the ticket is committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ClinicRoom room where upper(room.code) = upper(:code) and room.active = true")
    Optional<ClinicRoom> findByCodeAndActiveTrueForUpdate(@Param("code") String code);

    Optional<ClinicRoom> findByQrTokenAndActiveTrue(String qrToken);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<ClinicRoom> findAllByOrderByCodeAsc();
}

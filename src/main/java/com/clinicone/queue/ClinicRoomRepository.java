package com.clinicone.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClinicRoomRepository extends JpaRepository<ClinicRoom, UUID> {
    Optional<ClinicRoom> findByCodeAndActiveTrue(String code);

    Optional<ClinicRoom> findByQrTokenAndActiveTrue(String qrToken);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    java.util.List<ClinicRoom> findAllByOrderByCodeAsc();
}

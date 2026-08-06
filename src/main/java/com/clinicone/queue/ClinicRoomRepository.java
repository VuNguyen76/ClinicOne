package com.clinicone.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClinicRoomRepository extends JpaRepository<ClinicRoom, UUID> {
    Optional<ClinicRoom> findByCodeAndActiveTrue(String code);
}

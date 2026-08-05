package com.clinicone.patientprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, UUID> {
    List<PatientProfile> findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(UUID ownerId);
    Optional<PatientProfile> findByIdAndOwnerIdAndActiveTrue(UUID id, UUID ownerId);
    long countByOwnerIdAndActiveTrue(UUID ownerId);
}

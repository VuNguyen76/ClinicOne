package com.clinicone.medication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    List<Medication> findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String query);
    List<Medication> findByActiveTrueOrderByNameAsc();
    List<Medication> findAllByOrderByNameAsc();
    Optional<Medication> findByIdAndActiveTrue(UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}

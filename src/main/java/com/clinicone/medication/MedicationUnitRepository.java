package com.clinicone.medication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicationUnitRepository extends JpaRepository<MedicationUnit, UUID> {
    List<MedicationUnit> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<MedicationUnit> findAllByOrderBySortOrderAscNameAsc();

    Optional<MedicationUnit> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);
}

package com.clinicone.medication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationUnitRepository extends JpaRepository<MedicationUnit, UUID> {
    List<MedicationUnit> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<MedicationUnit> findAllByOrderBySortOrderAscNameAsc();

    Optional<MedicationUnit> findByCodeIgnoreCase(String code);

    Optional<MedicationUnit> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}

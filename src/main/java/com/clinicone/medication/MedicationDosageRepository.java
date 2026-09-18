package com.clinicone.medication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicationDosageRepository extends JpaRepository<MedicationDosage, UUID> {
    List<MedicationDosage> findByActiveTrueOrderBySortOrderAsc();

    List<MedicationDosage> findByActiveTrueAndUnitNameIgnoreCaseOrderBySortOrderAsc(String unitName);

    List<MedicationDosage> findAllByOrderBySortOrderAsc();

    Optional<MedicationDosage> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}

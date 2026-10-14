package com.clinicone.reason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicalReasonRepository extends JpaRepository<ClinicalReason, UUID> {
    List<ClinicalReason> findBySpecialtyNameIgnoreCaseAndActiveTrueOrderBySortOrderAsc(String specialtyName);

    List<ClinicalReason> findByActiveTrueOrderBySpecialtyNameAscSortOrderAsc();

    List<ClinicalReason> findAllByOrderBySpecialtyNameAscSortOrderAsc();

    boolean existsBySpecialtyNameIgnoreCaseAndSymptomNameIgnoreCase(String specialtyName, String symptomName);
}

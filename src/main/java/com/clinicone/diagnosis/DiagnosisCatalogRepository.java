package com.clinicone.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosisCatalogRepository extends JpaRepository<DiagnosisCatalog, UUID> {
    List<DiagnosisCatalog> findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String query);
    List<DiagnosisCatalog> findByActiveTrueOrderByNameAsc();
    List<DiagnosisCatalog> findAllByOrderByNameAsc();
    Optional<DiagnosisCatalog> findByIdAndActiveTrue(UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}

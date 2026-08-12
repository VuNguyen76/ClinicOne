package com.clinicone.reason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReasonCatalogRepository extends JpaRepository<ReasonCatalog, UUID> {
    List<ReasonCatalog> findByTypeAndActiveTrueOrderByLabelAsc(ReasonCatalogType type);
    List<ReasonCatalog> findByTypeOrderByLabelAsc(ReasonCatalogType type);
    Optional<ReasonCatalog> findByTypeAndCodeIgnoreCaseAndActiveTrue(ReasonCatalogType type, String code);
    boolean existsByTypeAndCodeIgnoreCase(ReasonCatalogType type, String code);
    boolean existsByTypeAndCodeIgnoreCaseAndIdNot(ReasonCatalogType type, String code, UUID id);
    long countByTypeAndActiveTrue(ReasonCatalogType type);
}

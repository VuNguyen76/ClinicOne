package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialtyCatalogRepository extends JpaRepository<SpecialtyCatalogEntry, UUID> {
    List<SpecialtyCatalogEntry> findByActiveTrueOrderByNameAsc();
    Optional<SpecialtyCatalogEntry> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}

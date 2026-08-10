package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {
    List<ClinicService> findAllByOrderByNameAsc();

    List<ClinicService> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCase(
            String name, String specialty, String visitType);

    boolean existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCaseAndIdNot(
            String name, String specialty, String visitType, UUID id);
}

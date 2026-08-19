package com.clinicone.schedule;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {
    @EntityGraph(attributePaths = {"eligibleDoctors", "eligibleDoctors.staffAccount"})
    List<ClinicService> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"eligibleDoctors", "eligibleDoctors.staffAccount"})
    List<ClinicService> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCase(
            String name, String specialty, String visitType);

    boolean existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCaseAndIdNot(
            String name, String specialty, String visitType, UUID id);
}

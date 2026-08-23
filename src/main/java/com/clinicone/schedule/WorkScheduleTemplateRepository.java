package com.clinicone.schedule;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkScheduleTemplateRepository extends JpaRepository<WorkScheduleTemplate, UUID> {
    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room", "weekdays", "breaks", "exceptionDates"})
    List<WorkScheduleTemplate> findByActiveTrueOrderByStartDateAsc();

    @Override
    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room", "weekdays", "breaks", "exceptionDates"})
    Optional<WorkScheduleTemplate> findById(UUID id);
}

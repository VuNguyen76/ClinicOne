package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkScheduleTemplateRepository extends JpaRepository<WorkScheduleTemplate, UUID> {
    List<WorkScheduleTemplate> findByActiveTrueOrderByStartDateAsc();
}

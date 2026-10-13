package com.clinicone.schedule;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkScheduleTemplateRepository extends JpaRepository<WorkScheduleTemplate, UUID> {
    @EntityGraph(attributePaths = {"doctorProfile", "doctorProfile.staffAccount", "weekdays", "exceptionDates"})
    @Query("""
            select distinct template from WorkScheduleTemplate template
            where template.clinicService.id = :serviceId
              and template.active = true
              and template.startDate <= :to
              and template.endDate >= :from
            """)
    List<WorkScheduleTemplate> findActiveByServiceAndDateRange(@Param("serviceId") UUID serviceId,
                                                                @Param("from") LocalDate from,
                                                                @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room",
            "weekdays", "breaks", "exceptionDates"})
    @Query("""
            select distinct template from WorkScheduleTemplate template
            where template.doctorProfile.staffAccount.id = :doctorStaffId
              and template.startDate <= :to
              and template.endDate >= :from
            """)
    List<WorkScheduleTemplate> findByDoctorStaffIdAndDateRange(@Param("doctorStaffId") UUID doctorStaffId,
                                                                @Param("from") LocalDate from,
                                                                @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room", "weekdays", "breaks", "exceptionDates"})
    List<WorkScheduleTemplate> findByActiveTrueOrderByStartDateAsc();

    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room",
            "weekdays", "breaks", "exceptionDates"})
    List<WorkScheduleTemplate> findByDoctorProfile_StaffAccount_IdOrderByStartDateAsc(UUID doctorStaffId);

    @Override
    @EntityGraph(attributePaths = {"clinicService", "doctorProfile", "doctorProfile.staffAccount", "room", "weekdays", "breaks", "exceptionDates"})
    Optional<WorkScheduleTemplate> findById(UUID id);
}

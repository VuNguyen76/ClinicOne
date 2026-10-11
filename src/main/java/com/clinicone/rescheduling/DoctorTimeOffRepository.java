package com.clinicone.rescheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DoctorTimeOffRepository extends JpaRepository<DoctorTimeOff, UUID> {
    List<DoctorTimeOff> findByActiveTrueOrderByStartDateAsc();

    @Query("select count(t) > 0 from DoctorTimeOff t "
            + "where t.doctorProfile.id = :profileId and t.active = true "
            + "and t.startDate <= :endDate and t.endDate >= :startDate")
    boolean existsOverlapping(@Param("profileId") UUID profileId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);
}

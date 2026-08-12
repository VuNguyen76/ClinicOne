package com.clinicone.doctor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {
    List<DoctorSchedule> findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(UUID doctorProfileId, DayOfWeek dayOfWeek);

    @EntityGraph(attributePaths = {"doctorProfile", "doctorProfile.staffAccount", "doctorProfile.room"})
    @Query("""
            select schedule
            from DoctorSchedule schedule
            join schedule.doctorProfile profile
            where lower(profile.specialty) = lower(:specialty)
              and profile.active = true
              and schedule.active = true
            order by profile.id, schedule.dayOfWeek, schedule.startTime
            """)
    List<DoctorSchedule> findActiveBySpecialtyIgnoreCase(@Param("specialty") String specialty);

    List<DoctorSchedule> findByDoctorProfile_IdOrderByDayOfWeekAscStartTimeAsc(UUID doctorProfileId);
}

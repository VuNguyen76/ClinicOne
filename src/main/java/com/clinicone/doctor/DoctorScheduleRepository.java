package com.clinicone.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {
    List<DoctorSchedule> findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(UUID doctorProfileId, DayOfWeek dayOfWeek);

    List<DoctorSchedule> findByDoctorProfile_IdOrderByDayOfWeekAscStartTimeAsc(UUID doctorProfileId);
}

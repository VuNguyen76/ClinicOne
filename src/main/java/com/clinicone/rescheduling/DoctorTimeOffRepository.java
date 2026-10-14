package com.clinicone.rescheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DoctorTimeOffRepository extends JpaRepository<DoctorTimeOff, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DoctorTimeOff t where t.id = :id")
    java.util.Optional<DoctorTimeOff> findByIdForUpdate(@Param("id") UUID id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"doctorProfile", "doctorProfile.staffAccount"})
    List<DoctorTimeOff> findByActiveTrueOrderByStartDateAsc();

    @Query("""
            select new com.clinicone.rescheduling.DoctorTimeOffWindow(
                timeOff.doctorProfile.staffAccount.id, timeOff.startDate, timeOff.endDate)
            from DoctorTimeOff timeOff
            where timeOff.active = true
              and timeOff.doctorProfile.staffAccount.id in :doctorIds
              and timeOff.startDate <= :to
              and timeOff.endDate >= :from
            """)
    List<DoctorTimeOffWindow> findActiveWindows(@Param("doctorIds") List<UUID> doctorIds,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    @Query("select count(t) > 0 from DoctorTimeOff t "
            + "where t.doctorProfile.id = :profileId and t.active = true "
            + "and t.startDate <= :endDate and t.endDate >= :startDate")
    boolean existsOverlapping(@Param("profileId") UUID profileId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    @Query("select count(t) > 0 from DoctorTimeOff t where t.doctorProfile.id = :profileId "
            + "and t.active = true and t.startDate <= :date and t.endDate >= :date")
    boolean existsActiveOnDate(@Param("profileId") UUID profileId, @Param("date") LocalDate date);

    @Query("select count(t) > 0 from DoctorTimeOff t where t.doctorProfile.id = :profileId "
            + "and t.active = true and t.id <> :excludedId and t.startDate <= :endDate and t.endDate >= :startDate")
    boolean existsOverlappingExcept(@Param("profileId") UUID profileId, @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate, @Param("excludedId") UUID excludedId);
}

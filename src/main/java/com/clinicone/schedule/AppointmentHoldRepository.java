package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentHoldRepository extends JpaRepository<AppointmentHold, UUID> {
    Optional<AppointmentHold> findByHoldKey(String holdKey);

    Optional<AppointmentHold> findByIdAndPatientId(UUID id, UUID patientId);

    List<AppointmentHold> findByExpiresAtLessThanEqual(Instant expiresAt);

    @Query("""
            select count(h) from AppointmentHold h
            where h.specialty = :specialty
              and h.appointmentDate = :appointmentDate
              and h.startTime = :startTime
              and h.expiresAt > :now
            """)
    long countActiveBySpecialtyAndSlot(@Param("specialty") String specialty,
                                       @Param("appointmentDate") LocalDate appointmentDate,
                                       @Param("startTime") LocalTime startTime,
                                       @Param("now") Instant now);

    @Query("""
            select count(h) from AppointmentHold h
            where h.specialty = :specialty
              and h.appointmentDate = :appointmentDate
              and h.startTime = :startTime
              and h.expiresAt > :now
              and h.id <> :holdId
            """)
    long countActiveBySpecialtyAndSlotExcludingHold(@Param("specialty") String specialty,
                                                    @Param("appointmentDate") LocalDate appointmentDate,
                                                    @Param("startTime") LocalTime startTime,
                                                    @Param("now") Instant now,
                                                    @Param("holdId") UUID holdId);

    @Query("""
            select count(h) from AppointmentHold h
            where h.doctorStaffId = :doctorStaffId
              and h.appointmentDate = :appointmentDate
              and h.startTime = :startTime
              and h.expiresAt > :now
            """)
    long countActiveByDoctorSlot(@Param("doctorStaffId") UUID doctorStaffId,
                                 @Param("appointmentDate") LocalDate appointmentDate,
                                 @Param("startTime") LocalTime startTime,
                                 @Param("now") Instant now);

    @Query("""
            select count(h) from AppointmentHold h
            where h.doctorStaffId = :doctorStaffId
              and h.appointmentDate = :appointmentDate
              and h.startTime = :startTime
              and h.expiresAt > :now
              and h.id <> :holdId
            """)
    long countActiveByDoctorSlotExcludingHold(@Param("doctorStaffId") UUID doctorStaffId,
                                              @Param("appointmentDate") LocalDate appointmentDate,
                                              @Param("startTime") LocalTime startTime,
                                              @Param("now") Instant now,
                                              @Param("holdId") UUID holdId);

    @Query("""
            select h from AppointmentHold h
            where h.specialty = :specialty
              and h.appointmentDate between :from and :to
              and h.expiresAt > :now
            """)
    List<AppointmentHold> findActiveBySpecialtyAndDateRange(@Param("specialty") String specialty,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to,
                                                             @Param("now") Instant now);

    @Query("""
            select h from AppointmentHold h
            where h.doctorStaffId in :doctorStaffIds
              and h.appointmentDate between :from and :to
              and h.expiresAt > :now
            """)
    List<AppointmentHold> findActiveByDoctorsAndDateRange(@Param("doctorStaffIds") List<UUID> doctorStaffIds,
                                                           @Param("from") LocalDate from,
                                                           @Param("to") LocalDate to,
                                                           @Param("now") Instant now);
}

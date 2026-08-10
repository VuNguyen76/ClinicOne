package com.clinicone.appointment;

import com.clinicone.schedule.SlotBookingCount;
import com.clinicone.schedule.DoctorSlotBookingCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByPatientIdOrderByAppointmentDateAscStartTimeAsc(UUID patientId);

    Optional<Appointment> findByPatientIdAndAppointmentDateAndStartTimeAndStatus(UUID patientId, LocalDate appointmentDate,
                                                                                   LocalTime startTime, AppointmentStatus status);

    long countBySpecialtyAndAppointmentDateAndStartTimeAndStatus(String specialty, LocalDate appointmentDate,
                                                                  LocalTime startTime, AppointmentStatus status);

    long countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(UUID doctorStaffId, LocalDate appointmentDate,
                                                                       LocalTime startTime, AppointmentStatus status);

    @Query("""
            select new com.clinicone.schedule.SlotBookingCount(a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.specialty = :specialty
              and a.appointmentDate between :from and :to
              and a.status = :status
            group by a.appointmentDate, a.startTime
            """)
    List<SlotBookingCount> countBookedBySpecialtyAndDateRange(
            @Param("specialty") String specialty,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);

    @Query("""
            select new com.clinicone.schedule.DoctorSlotBookingCount(
                a.doctorStaffId, a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.doctorStaffId in :doctorStaffIds
              and a.appointmentDate between :from and :to
              and a.status = :status
            group by a.doctorStaffId, a.appointmentDate, a.startTime
            """)
    List<DoctorSlotBookingCount> countBookedByDoctorsAndDateRange(
            @Param("doctorStaffIds") List<UUID> doctorStaffIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);

    Optional<Appointment> findByIdAndPatientId(UUID appointmentId, UUID patientId);

    Optional<Appointment> findByAppointmentCode(String appointmentCode);

    @Query("""
            select a from Appointment a
            join fetch a.patient p
            left join fetch a.patientProfile profile
            where a.status = :status
              and a.appointmentDate = :appointmentDate
              and (lower(a.appointmentCode) = lower(:query) or p.phone = :query or profile.phone = :query)
            order by a.startTime asc
            """)
    List<Appointment> findReceptionCandidates(@Param("query") String query,
                                               @Param("appointmentDate") LocalDate appointmentDate,
                                               @Param("status") AppointmentStatus status);
}

package com.clinicone.appointment;

import com.clinicone.schedule.SlotBookingCount;
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

    Optional<Appointment> findByIdAndPatientId(UUID appointmentId, UUID patientId);
}

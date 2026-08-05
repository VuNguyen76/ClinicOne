package com.clinicone.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<Appointment> findByIdAndPatientId(UUID appointmentId, UUID patientId);
}

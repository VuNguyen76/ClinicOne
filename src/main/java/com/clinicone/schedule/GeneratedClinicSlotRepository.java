package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GeneratedClinicSlotRepository extends JpaRepository<GeneratedClinicSlot, UUID> {
    List<GeneratedClinicSlot> findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(UUID templateId);

    List<GeneratedClinicSlot> findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
            UUID doctorStaffId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByRoomIdAndAppointmentDateBetweenAndStatus(
            UUID roomId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByClinicServiceIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
            UUID clinicServiceId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
            UUID clinicServiceId, UUID doctorStaffId, LocalDate appointmentDate, java.time.LocalTime startTime,
            GeneratedSlotStatus status);
}

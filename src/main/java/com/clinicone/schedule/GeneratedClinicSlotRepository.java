package com.clinicone.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedClinicSlotRepository extends JpaRepository<GeneratedClinicSlot, UUID> {
    List<GeneratedClinicSlot> findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(UUID templateId);

    List<GeneratedClinicSlot> findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
            UUID doctorStaffId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByRoomIdAndAppointmentDateBetweenAndStatus(
            UUID roomId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByClinicServiceIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
            UUID clinicServiceId, LocalDate from, LocalDate to, GeneratedSlotStatus status);

    List<GeneratedClinicSlot> findByClinicServiceIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
            UUID clinicServiceId, LocalDate from, LocalDate to);

    List<GeneratedClinicSlot> findByClinicServiceIdAndDoctorStaffIdAndAppointmentDate(
            UUID clinicServiceId, UUID doctorStaffId, LocalDate appointmentDate);

    List<GeneratedClinicSlot> findByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
            UUID clinicServiceId, UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime,
            GeneratedSlotStatus status);


    List<GeneratedClinicSlot> findByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTime(
            UUID clinicServiceId, UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime);

    Optional<GeneratedClinicSlot> findFirstByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
            UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime, GeneratedSlotStatus status);

    Optional<GeneratedClinicSlot> findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
            UUID clinicServiceId, UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime,
            GeneratedSlotStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM GeneratedClinicSlot s WHERE s.id IN :slotIds")
    void deleteAllByIdIn(@org.springframework.data.repository.query.Param("slotIds") java.util.Collection<UUID> slotIds);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM GeneratedClinicSlot s WHERE s.template.id = :templateId AND s.status = :status")
    void deleteByTemplateIdAndStatus(
            @org.springframework.data.repository.query.Param("templateId") UUID templateId,
            @org.springframework.data.repository.query.Param("status") GeneratedSlotStatus status);
}

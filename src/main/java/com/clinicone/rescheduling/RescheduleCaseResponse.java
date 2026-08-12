package com.clinicone.rescheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RescheduleCaseResponse(
        UUID id,
        UUID appointmentId,
        String appointmentCode,
        String specialty,
        String oldDoctorName,
        UUID oldDoctorId,
        LocalDate oldAppointmentDate,
        LocalTime oldStartTime,
        String reason,
        RescheduleCaseStatus status,
        String newDoctorName,
        UUID newDoctorId,
        LocalDate newAppointmentDate,
        LocalTime newStartTime,
        Instant createdAt,
        Instant resolvedAt
) {
    static RescheduleCaseResponse from(RescheduleCase item) {
        return new RescheduleCaseResponse(item.getId(), item.getAppointment().getId(),
                item.getAppointment().getAppointmentCode(), item.getSpecialty(), item.getOldDoctorName(),
                item.getOldDoctorStaffId(), item.getOldAppointmentDate(), item.getOldStartTime(), item.getReason(),
                item.getStatus(), item.getNewDoctorName(), item.getNewDoctorStaffId(), item.getNewAppointmentDate(),
                item.getNewStartTime(), item.getCreatedAt(), item.getResolvedAt());
    }
}

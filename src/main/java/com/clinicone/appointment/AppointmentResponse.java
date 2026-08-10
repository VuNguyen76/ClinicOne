package com.clinicone.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        String appointmentCode,
        String specialty,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        String reason,
        String status,
        String statusLabel,
        UUID profileId,
        String profileName,
        UUID doctorId,
        UUID serviceId,
        String serviceName,
        String visitType,
        Integer serviceDurationMinutes
) {
    public AppointmentResponse(UUID id, String appointmentCode, String specialty, String doctorName,
                               LocalDate appointmentDate, LocalTime startTime, String reason,
                               String status, String statusLabel) {
        this(id, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason, status, statusLabel,
                null, null, null, null, null, null, null);
    }

    public AppointmentResponse(UUID id, String appointmentCode, String specialty, String doctorName,
                               LocalDate appointmentDate, LocalTime startTime, String reason,
                               String status, String statusLabel, UUID profileId, String profileName,
                               UUID doctorId) {
        this(id, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason, status,
                statusLabel, profileId, profileName, doctorId, null, null, null, null);
    }

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentCode(), appointment.getSpecialty(),
                appointment.getDoctorName(), appointment.getAppointmentDate(), appointment.getStartTime(),
                appointment.getReason(), appointment.getStatus().name(), appointment.getStatus().label(),
                appointment.getPatientProfile() == null ? null : appointment.getPatientProfile().getId(),
                appointment.getPatientProfile() == null ? null : appointment.getPatientProfile().getFullName(),
                appointment.getDoctorStaffId(), appointment.getServiceId(), appointment.getServiceName(),
                appointment.getVisitType(), appointment.getServiceDurationMinutes());
    }
}

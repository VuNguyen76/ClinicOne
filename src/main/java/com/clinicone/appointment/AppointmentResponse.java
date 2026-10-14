package com.clinicone.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.ClinicRoom;

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
        Integer serviceDurationMinutes,
        boolean requiresMedicalRecord,
        String roomCode,
        String roomName
) {
    public AppointmentResponse(UUID id, String appointmentCode, String specialty, String doctorName,
                               LocalDate appointmentDate, LocalTime startTime, String reason,
                               String status, String statusLabel, UUID profileId, String profileName,
                               UUID doctorId, UUID serviceId, String serviceName, String visitType,
                               Integer serviceDurationMinutes, boolean requiresMedicalRecord) {
        this(id, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason, status, statusLabel,
                profileId, profileName, doctorId, serviceId, serviceName, visitType, serviceDurationMinutes,
                requiresMedicalRecord, null, null);
    }

    public AppointmentResponse(UUID id, String appointmentCode, String specialty, String doctorName,
                               LocalDate appointmentDate, LocalTime startTime, String reason,
                               String status, String statusLabel) {
        this(id, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason, status, statusLabel,
                null, null, null, null, null, null, null, true);
    }

    public AppointmentResponse(UUID id, String appointmentCode, String specialty, String doctorName,
                               LocalDate appointmentDate, LocalTime startTime, String reason,
                               String status, String statusLabel, UUID profileId, String profileName,
                               UUID doctorId) {
        this(id, appointmentCode, specialty, doctorName, appointmentDate, startTime, reason, status,
                statusLabel, profileId, profileName, doctorId, null, null, null, null, true);
    }

    public static AppointmentResponse from(Appointment appointment) {
        return from(appointment, null);
    }

    public static AppointmentResponse from(Appointment appointment, QueueTicket ticket) {
        return from(appointment, ticket, null);
    }

    public static AppointmentResponse from(Appointment appointment, QueueTicket ticket, ClinicRoom assignedRoom) {
        String effectiveSpecialty = ticket != null && ticket.getEffectiveSpecialty() != null ? ticket.getEffectiveSpecialty() : appointment.getSpecialty();
        String effectiveDoctorName = ticket != null && ticket.getEffectiveDoctorName() != null ? ticket.getEffectiveDoctorName() : appointment.getDoctorName();
        ClinicRoom room = ticket != null && ticket.getRoom() != null ? ticket.getRoom() : assignedRoom;
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentCode(), effectiveSpecialty,
                effectiveDoctorName, appointment.getAppointmentDate(), appointment.getStartTime(),
                appointment.getReason(), appointment.getStatus().name(), appointment.getStatus().label(),
                appointment.getPatientProfile() == null ? null : appointment.getPatientProfile().getId(),
                appointment.getPatientProfile() == null ? null : appointment.getPatientProfile().getFullName(),
                appointment.getDoctorStaffId(), appointment.getServiceId(), appointment.getServiceName(),
                appointment.getVisitType(), appointment.getServiceDurationMinutes(), appointment.requiresMedicalRecord(),
                room == null ? null : room.getCode(), room == null ? null : room.getName());
    }
}

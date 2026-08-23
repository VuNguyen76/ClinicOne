package com.clinicone.reception;

import com.clinicone.appointment.Appointment;
import com.clinicone.queue.QueueTicketResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ReceptionAppointmentResponse(
        UUID id,
        String appointmentCode,
        LocalDate appointmentDate,
        LocalTime startTime,
        String specialty,
        String doctorName,
        String roomCode,
        String roomName,
        UUID patientProfileId,
        String patientName,
        String patientPhone,
        String status,
        Integer queueNumber,
        String queueStatus,
        String queueStatusLabel,
        String queuePresenceStatus,
        String queuePresenceLabel,
        UUID queueTicketId,
        boolean queuePriority,
        String queueClosureOutcome,
        String queueClosureOutcomeLabel,
        boolean lateArrival
) {
    public ReceptionAppointmentResponse(UUID id, String appointmentCode, LocalDate appointmentDate,
                                        LocalTime startTime, String specialty, String doctorName,
                                        String roomCode, String roomName, UUID patientProfileId,
                                        String patientName, String patientPhone, String status,
                                        Integer queueNumber, String queueStatus, String queueStatusLabel) {
        this(id, appointmentCode, appointmentDate, startTime, specialty, doctorName, roomCode, roomName,
                patientProfileId, patientName, patientPhone, status, queueNumber, queueStatus, queueStatusLabel,
                null, null, null, false, null, null, false);
    }

    public ReceptionAppointmentResponse(UUID id, String appointmentCode, LocalDate appointmentDate,
                                        LocalTime startTime, String specialty, String doctorName,
                                        String roomCode, String roomName, UUID patientProfileId,
                                        String patientName, String patientPhone, String status,
                                        Integer queueNumber, String queueStatus, String queueStatusLabel,
                                        String queuePresenceStatus, String queuePresenceLabel, UUID queueTicketId,
                                        boolean queuePriority) {
        this(id, appointmentCode, appointmentDate, startTime, specialty, doctorName, roomCode, roomName,
                patientProfileId, patientName, patientPhone, status, queueNumber, queueStatus, queueStatusLabel,
                queuePresenceStatus, queuePresenceLabel, queueTicketId, queuePriority, null, null, false);
    }

    public static ReceptionAppointmentResponse from(Appointment appointment, String roomCode, String roomName,
                                                     QueueTicketResponse ticket) {
        return from(appointment, roomCode, roomName, ticket, false);
    }

    public static ReceptionAppointmentResponse from(Appointment appointment, String roomCode, String roomName,
                                                     QueueTicketResponse ticket, boolean lateArrival) {
        var profile = appointment.getPatientProfile();
        var patient = appointment.getPatient();
        return new ReceptionAppointmentResponse(appointment.getId(), appointment.getAppointmentCode(),
                appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getSpecialty(),
                appointment.getDoctorName(), roomCode, roomName,
                profile == null ? null : profile.getId(), profile == null && patient == null ? null
                        : profile == null ? patient.getFullName() : profile.getFullName(),
                profile == null && patient == null ? null : profile == null ? patient.getPhone() : profile.getPhone(),
                appointment.getStatus().name(),
                ticket == null ? null : ticket.queueNumber(), ticket == null ? null : ticket.status(),
                ticket == null ? null : ticket.statusLabel(), ticket == null ? null : ticket.presenceStatus(),
                ticket == null ? null : ticket.presenceLabel(), ticket == null ? null : ticket.id(),
                ticket != null && ticket.priority(), ticket == null ? null : ticket.closureOutcome(),
                ticket == null ? null : ticket.closureOutcomeLabel(), lateArrival);
    }

}

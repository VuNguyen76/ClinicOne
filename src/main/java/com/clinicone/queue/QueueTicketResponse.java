package com.clinicone.queue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.UUID;

public record QueueTicketResponse(
        UUID id,
        int queueNumber,
        String roomCode,
        String roomName,
        LocalDate queueDate,
        LocalTime appointmentTime,
        String status,
        String statusLabel,
        String presenceStatus,
        String presenceLabel,
        Instant returnedAt,
        String appointmentCode,
        String specialty,
        String doctorName,
        boolean priority,
        String closureOutcome,
        String closureOutcomeLabel
) {
    public QueueTicketResponse(UUID id, int queueNumber, String roomCode, String roomName,
                               LocalDate queueDate, LocalTime appointmentTime, String status,
                               String statusLabel, String appointmentCode, String specialty,
                               String doctorName) {
        this(id, queueNumber, roomCode, roomName, queueDate, appointmentTime, status, statusLabel,
                QueuePresenceStatus.READY.name(), QueuePresenceStatus.READY.label(), null,
                appointmentCode, specialty, doctorName, false, null, null);
    }

    public static QueueTicketResponse from(QueueTicket ticket) {
        var appointment = ticket.getAppointment();
        return new QueueTicketResponse(ticket.getId(), ticket.getQueueNumber(), ticket.getRoom().getCode(),
                ticket.getRoom().getName(), ticket.getQueueDate(), appointment.getStartTime(),
                ticket.getStatus().name(), ticket.getStatus().label(), ticket.getPresenceStatus().name(),
                ticket.getPresenceLabel(), ticket.getReturnedAt(), appointment.getAppointmentCode(),
                ticket.getEffectiveSpecialty(), ticket.getEffectiveDoctorName(), ticket.isPriority(),
                ticket.getClosureOutcome() == null ? null : ticket.getClosureOutcome().name(),
                ticket.getClosureOutcomeLabel());
    }
}

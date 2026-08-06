package com.clinicone.queue;

import java.time.LocalDate;
import java.time.LocalTime;
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
        String appointmentCode,
        String specialty,
        String doctorName
) {
    public static QueueTicketResponse from(QueueTicket ticket) {
        var appointment = ticket.getAppointment();
        return new QueueTicketResponse(ticket.getId(), ticket.getQueueNumber(), ticket.getRoom().getCode(),
                ticket.getRoom().getName(), ticket.getQueueDate(), appointment.getStartTime(),
                ticket.getStatus().name(), ticket.getStatus().label(), appointment.getAppointmentCode(),
                appointment.getSpecialty(), appointment.getDoctorName());
    }
}

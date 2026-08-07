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
        String queueStatusLabel
) {
    public static ReceptionAppointmentResponse from(Appointment appointment, String roomCode, String roomName,
                                                     QueueTicketResponse ticket) {
        var profile = appointment.getPatientProfile();
        return new ReceptionAppointmentResponse(appointment.getId(), appointment.getAppointmentCode(),
                appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getSpecialty(),
                appointment.getDoctorName(), roomCode, roomName,
                profile == null ? null : profile.getId(), profile == null ? appointment.getPatient().getFullName() : profile.getFullName(),
                profile == null ? appointment.getPatient().getPhone() : profile.getPhone(), appointment.getStatus().name(),
                ticket == null ? null : ticket.queueNumber(), ticket == null ? null : ticket.status(),
                ticket == null ? null : ticket.statusLabel());
    }
}

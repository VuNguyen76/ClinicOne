package com.clinicone.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, UUID> {
    @EntityGraph(attributePaths = {"appointment", "room"})
    Optional<QueueTicket> findByAppointmentId(UUID appointmentId);

    @EntityGraph(attributePaths = {"appointment", "room"})
    List<QueueTicket> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    @Query("select max(ticket.queueNumber) from QueueTicket ticket "
            + "where ticket.room.code = :roomCode and ticket.queueDate = :queueDate")
    Integer findMaxQueueNumberByRoomCodeAndQueueDate(@Param("roomCode") String roomCode,
                                                      @Param("queueDate") LocalDate queueDate);

    @EntityGraph(attributePaths = {"appointment", "room"})
    List<QueueTicket> findByRoomCodeAndQueueDateOrderByQueueNumberAsc(String roomCode, LocalDate queueDate);

    @EntityGraph(attributePaths = {"appointment", "room"})
    List<QueueTicket> findByAppointment_Patient_IdAndQueueDateOrderByQueueNumberAsc(UUID patientId,
                                                                                       LocalDate queueDate);

    @EntityGraph(attributePaths = {"appointment", "room"})
    List<QueueTicket> findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
            String roomCode, LocalDate queueDate, UUID doctorStaffId);

    @EntityGraph(attributePaths = {"appointment", "room"})
    List<QueueTicket> findByRoomCodeAndQueueDateAndRoutingDoctorStaffIdOrderByQueueNumberAsc(
            String roomCode, LocalDate queueDate, UUID doctorStaffId);

    @Query("select count(ticket) from QueueTicket ticket "
            + "where ticket.status = com.clinicone.queue.QueueTicketStatus.IN_SERVICE "
            + "and ticket.id <> :ticketId "
            + "and (ticket.routingDoctorStaffId = :doctorId "
            + "or (ticket.routingDoctorStaffId is null and ticket.appointment.doctorStaffId = :doctorId))")
    long countInServiceForDoctorExcludingTicket(@Param("doctorId") UUID doctorId, @Param("ticketId") UUID ticketId);
}

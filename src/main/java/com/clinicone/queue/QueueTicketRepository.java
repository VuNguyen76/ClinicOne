package com.clinicone.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, UUID> {
    Optional<QueueTicket> findByAppointmentId(UUID appointmentId);

    @Query("select max(ticket.queueNumber) from QueueTicket ticket "
            + "where ticket.room.code = :roomCode and ticket.queueDate = :queueDate")
    Integer findMaxQueueNumberByRoomCodeAndQueueDate(@Param("roomCode") String roomCode,
                                                      @Param("queueDate") LocalDate queueDate);

    List<QueueTicket> findByRoomCodeAndQueueDateOrderByQueueNumberAsc(String roomCode, LocalDate queueDate);
}

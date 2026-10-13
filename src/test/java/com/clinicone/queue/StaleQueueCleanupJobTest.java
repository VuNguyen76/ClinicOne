package com.clinicone.queue;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.examination.ExaminationSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StaleQueueCleanupJobTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private final QueueTicketRepository ticketRepository = mock(QueueTicketRepository.class);
    private final ExaminationSessionRepository sessionRepository = mock(ExaminationSessionRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final BusinessLogService businessLogService = mock(BusinessLogService.class);

    @Test
    void selectsQueueTicketsOnlyAfterTwentyFourHoursSinceCheckIn() {
        StaleQueueCleanupJob job = new StaleQueueCleanupJob(ticketRepository, sessionRepository,
                appointmentRepository, businessLogService, Clock.fixed(NOW, ZoneOffset.UTC));

        job.runOnce();

        verify(ticketRepository).findByStatusInAndCheckedInAtBefore(
                List.of(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED, QueueTicketStatus.IN_SERVICE),
                NOW.minus(Duration.ofHours(24)));
    }
}

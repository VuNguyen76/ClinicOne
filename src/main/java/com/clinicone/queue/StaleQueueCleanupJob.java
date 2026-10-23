package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class StaleQueueCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(StaleQueueCleanupJob.class);
    private static final Duration STALE_AFTER = Duration.ofHours(24);
    private static final String AUTO_REASON = "Tự động dọn sau 24h không hoàn tất";

    private final QueueTicketRepository ticketRepository;
    private final ExaminationSessionRepository sessionRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessLogService businessLogService;
    private final Clock clock;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private com.clinicone.rescheduling.RescheduleCaseRepository rescheduleCaseRepository;

    public StaleQueueCleanupJob(QueueTicketRepository ticketRepository,
                                ExaminationSessionRepository sessionRepository,
                                AppointmentRepository appointmentRepository,
                                BusinessLogService businessLogService,
                                Clock clock) {
        this.ticketRepository = ticketRepository;
        this.sessionRepository = sessionRepository;
        this.appointmentRepository = appointmentRepository;
        this.businessLogService = businessLogService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Scheduled(fixedDelayString = "${app.queue.cleanup-job-delay-ms:3600000}")
    public void runScheduled() {
        runOnce();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        runOnce();
    }

    public CleanupResult runOnce() {
        Instant staleBefore = Instant.now(clock).minus(STALE_AFTER);
        List<QueueTicket> stale = ticketRepository.findByStatusInAndCheckedInAtBefore(
                List.of(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED, QueueTicketStatus.IN_SERVICE), staleBefore);
        int inspected = stale.size();
        int[] counters = new int[2]; // [0] = stopped, [1] = leftBefore
        for (QueueTicket ticket : stale) {
            try {
                if (transactionManager != null) {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> processTicket(ticket, counters));
                } else {
                    processTicket(ticket, counters);
                }
            } catch (Exception e) {
                log.warn("Stale cleanup failed for ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }
        int stopped = counters[0];
        int leftBefore = counters[1];
        if (inspected > 0) {
            log.info("Stale cleanup: inspected={}, stopped={}, leftBefore={}", inspected, stopped, leftBefore);
        }
        return new CleanupResult(inspected, stopped, leftBefore);
    }

    private void processTicket(QueueTicket ticket, int[] counters) {
        QueueTicket fresh = ticketRepository.findById(ticket.getId()).orElse(null);
        if (fresh == null) return;
        if (fresh.getStatus() == QueueTicketStatus.COMPLETED
                || fresh.getStatus() == QueueTicketStatus.LEFT_BEFORE_EXAM
                || fresh.getStatus() == QueueTicketStatus.SKIPPED) return;
        Appointment appointment = fresh.getAppointment();
        ExaminationSession session = sessionRepository.findByAppointment_Id(appointment.getId()).orElse(null);

        if (fresh.getStatus() == QueueTicketStatus.IN_SERVICE) {
            if (stopStaleInService(fresh, appointment, session)) counters[0]++;
        } else {
            if (closeStaleWaiting(fresh, appointment, session)) counters[1]++;
        }
    }

    private boolean stopStaleInService(QueueTicket ticket, Appointment appointment, ExaminationSession session) {
        try {
            String prevTicket = ticket.getStatus().name();
            ticket.stopService(AUTO_REASON);
            ticketRepository.save(ticket);
            businessLogService.recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(),
                    prevTicket, ticket.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
        } catch (IllegalStateException e) {
            return false;
        }
        try {
            String prevAppt = appointment.getStatus().name();
            appointment.markNotPerformed();
            closeObsoleteRescheduleCase(appointment, "NOT_PERFORMED");
            appointmentRepository.save(appointment);
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", appointment.getId(),
                    prevAppt, appointment.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
        } catch (IllegalStateException ignored) {}
        if (session != null) {
            try {
                String prevSess = session.getStatus().name();
                session.stop();
                sessionRepository.save(session);
                businessLogService.recordTransition(UUID.randomUUID(), "EXAMINATION", session.getId(),
                        prevSess, session.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
            } catch (IllegalStateException ignored) {}
        }
        return true;
    }

    private boolean closeStaleWaiting(QueueTicket ticket, Appointment appointment, ExaminationSession session) {
        try {
            String prevTicket = ticket.getStatus().name();
            ticket.leaveBeforeExam(AUTO_REASON);
            ticketRepository.save(ticket);
            businessLogService.recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(),
                    prevTicket, ticket.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
        } catch (IllegalStateException e) {
            return false;
        }
        try {
            String prevAppt = appointment.getStatus().name();
            appointment.markNotPerformed();
            closeObsoleteRescheduleCase(appointment, "NOT_PERFORMED");
            appointmentRepository.save(appointment);
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", appointment.getId(),
                    prevAppt, appointment.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
        } catch (IllegalStateException ignored) {}
        if (session != null) {
            try {
                String prevSess = session.getStatus().name();
                session.cancel();
                sessionRepository.save(session);
                businessLogService.recordTransition(UUID.randomUUID(), "EXAMINATION", session.getId(),
                        prevSess, session.getStatus().name(), "AUTO_CLEANUP_STALE", "SYSTEM", AUTO_REASON);
            } catch (IllegalStateException ignored) {}
        }
        return true;
    }

    private void closeObsoleteRescheduleCase(Appointment appointment, String outcome) {
        if (rescheduleCaseRepository != null && appointment != null && appointment.getId() != null) {
            rescheduleCaseRepository.findByAppointmentIdAndStatus(appointment.getId(), com.clinicone.rescheduling.RescheduleCaseStatus.OPEN)
                    .ifPresent(rc -> {
                        rc.closeAsObsolete(outcome, Instant.now(clock));
                        rescheduleCaseRepository.save(rc);
                    });
        }
    }

    public record CleanupResult(int inspected, int stopped, int leftBefore) {}
}

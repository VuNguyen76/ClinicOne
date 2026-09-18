package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
public class StaleQueueCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(StaleQueueCleanupJob.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String AUTO_REASON = "Tự động dọn sau 24h không hoàn tất";

    private final QueueTicketRepository ticketRepository;
    private final ExaminationSessionRepository sessionRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessLogService businessLogService;
    private final Clock clock;

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
    @Transactional
    public void runScheduled() {
        runOnce();
    }

    @Transactional
    public CleanupResult runOnce() {
        LocalDate today = Instant.now(clock).atZone(CLINIC_ZONE).toLocalDate();
        List<QueueTicket> stale = ticketRepository.findByStatusInAndQueueDateBefore(
                List.of(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED, QueueTicketStatus.IN_SERVICE), today);
        int inspected = stale.size();
        int stopped = 0;
        int leftBefore = 0;
        for (QueueTicket ticket : stale) {
            try {
                QueueTicket fresh = ticketRepository.findById(ticket.getId()).orElse(null);
                if (fresh == null) continue;
                if (fresh.getStatus() == QueueTicketStatus.COMPLETED
                        || fresh.getStatus() == QueueTicketStatus.LEFT_BEFORE_EXAM
                        || fresh.getStatus() == QueueTicketStatus.SKIPPED) continue;
                Appointment appointment = fresh.getAppointment();
                ExaminationSession session = sessionRepository.findByAppointment_Id(appointment.getId()).orElse(null);

                if (fresh.getStatus() == QueueTicketStatus.IN_SERVICE) {
                    if (stopStaleInService(fresh, appointment, session)) stopped++;
                } else {
                    if (closeStaleWaiting(fresh, appointment, session)) leftBefore++;
                }
            } catch (Exception e) {
                log.warn("Stale cleanup failed for ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }
        if (inspected > 0) {
            log.info("Stale cleanup: inspected={}, stopped={}, leftBefore={}", inspected, stopped, leftBefore);
        }
        return new CleanupResult(inspected, stopped, leftBefore);
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

    public record CleanupResult(int inspected, int stopped, int leftBefore) {}
}

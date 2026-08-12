package com.clinicone.audit;

import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.reconciliation.ReconciliationIncident;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import com.clinicone.reconciliation.ReconciliationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciles the append-only journal with the current aggregate state. The
 * check is intentionally read-only for business data; a mismatch creates an
 * open incident, and BusinessLogService blocks later transitions on it.
 */
@Component
public class BusinessLogIntegrityJob {
    private static final Logger log = LoggerFactory.getLogger(BusinessLogIntegrityJob.class);

    private final BusinessLogRepository businessLogRepository;
    private final ReconciliationIncidentRepository incidentRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final ExaminationSessionRepository examinationSessionRepository;
    private final Clock clock;

    public BusinessLogIntegrityJob(BusinessLogRepository businessLogRepository,
                                   ReconciliationIncidentRepository incidentRepository,
                                   AppointmentRepository appointmentRepository,
                                   QueueTicketRepository queueTicketRepository,
                                   ExaminationSessionRepository examinationSessionRepository,
                                   Clock clock) {
        this.businessLogRepository = businessLogRepository;
        this.incidentRepository = incidentRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.examinationSessionRepository = examinationSessionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.audit.integrity-job-delay-ms:300000}")
    public void runScheduled() {
        runOnce();
    }

    /** Runs once after persistence has recovered and the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void runAfterRecovery() {
        runOnce();
    }

    @Transactional
    public IntegrityCheckResult runOnce() {
        Map<EntityKey, BusinessLog> latestByEntity = new HashMap<>();
        for (BusinessLog entry : businessLogRepository.findAllByOrderByOccurredAtAscIdAsc()) {
            latestByEntity.put(new EntityKey(entry.getEntityType(), entry.getEntityId()), entry);
        }
        int inspected = 0;
        int opened = 0;
        for (Map.Entry<EntityKey, BusinessLog> item : latestByEntity.entrySet()) {
            inspected++;
            EntityKey key = item.getKey();
            BusinessLog latest = item.getValue();
            String actual = currentStatus(key);
            if (actual != null && actual.equals(latest.getNextStatus())) {
                continue;
            }
            if (incidentRepository.existsByEntityTypeAndEntityIdAndStatus(key.entityType(), key.entityId(),
                    ReconciliationStatus.OPEN)) {
                continue;
            }
            String reason = actual == null
                    ? "Không tìm thấy đối tượng tương ứng với nhật ký nghiệp vụ " + latest.getEventId()
                    : "Trạng thái hiện tại " + actual + " không khớp nhật ký " + latest.getNextStatus();
            ReconciliationIncident incident = ReconciliationIncident.open(
                    "INC-INTEGRITY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                    key.entityType(), key.entityId(), latest.getEventId(), reason, "admin");
            incidentRepository.save(incident);
            log.warn("Opened integrity reconciliation for {} {}", key.entityType(), key.entityId());
            opened++;
        }
        return new IntegrityCheckResult(inspected, opened);
    }

    private String currentStatus(EntityKey key) {
        return switch (key.entityType().toUpperCase()) {
            case "APPOINTMENT" -> appointmentRepository.findById(key.entityId())
                    .map(item -> item.getStatus().name()).orElse(null);
            case "QUEUE_TICKET" -> queueTicketRepository.findById(key.entityId())
                    .map(item -> item.getStatus().name()).orElse(null);
            case "EXAMINATION" -> examinationSessionRepository.findById(key.entityId())
                    .map(item -> item.getStatus().name()).orElse(null);
            default -> null;
        };
    }

    private record EntityKey(String entityType, UUID entityId) {
    }

    public record IntegrityCheckResult(int inspected, int incidentsOpened) {
    }
}

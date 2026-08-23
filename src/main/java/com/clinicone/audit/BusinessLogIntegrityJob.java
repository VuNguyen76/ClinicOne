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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    @Transactional
    @Scheduled(fixedDelayString = "${app.audit.integrity-job-delay-ms:300000}")
    public void runScheduled() {
        runOnce();
    }

    /** Runs once after persistence has recovered and the application is ready. */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void runAfterRecovery() {
        runOnce();
    }

    @Transactional
    public IntegrityCheckResult runOnce() {
        Map<EntityKey, BusinessLog> latestByEntity = new HashMap<>();
        String previousHash = null;
        int hashIncidents = 0;
        for (BusinessLog entry : businessLogRepository.findAllByOrderByOccurredAtAscIdAsc()) {
            if (entry.getHash() != null && !entry.isHashValid(previousHash)
                    && entry.getId() != null
                    && !incidentRepository.existsByEntityTypeAndEntityIdAndStatus(
                    "BUSINESS_LOG", entry.getId(), ReconciliationStatus.OPEN)) {
                ReconciliationIncident incident = ReconciliationIncident.open(
                        generateIncidentCode(),
                        "BUSINESS_LOG", entry.getId(), entry.getEventId(),
                        "Chuỗi hash nhật ký nghiệp vụ không hợp lệ.", "admin");
                incidentRepository.save(incident);
                hashIncidents++;
            }
            if (entry.getHash() != null) {
                previousHash = entry.getHash();
            }
            latestByEntity.put(new EntityKey(entry.getEntityType(), entry.getEntityId()), entry);
        }
        int inspected = 0;
        int opened = hashIncidents;
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
            String reason = buildDiscrepancyReason(key, latest, actual);
            ReconciliationIncident incident = ReconciliationIncident.open(
                    generateIncidentCode(),
                    key.entityType(), key.entityId(), latest.getEventId(), reason, "admin");
            incidentRepository.save(incident);
            log.warn("Opened integrity reconciliation for {} {}", key.entityType(), key.entityId());
            opened++;
        }
        return new IntegrityCheckResult(inspected, opened);
    }

    private String generateIncidentCode() {
        String yearMonth = DateTimeFormatter.ofPattern("uuuuMM").format(LocalDate.now(clock));
        String shortCode = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SC-" + yearMonth + "-" + shortCode;
    }

    private String buildDiscrepancyReason(EntityKey key, BusinessLog latest, String actual) {
        String entityType = key.entityType().toUpperCase();
        if ("APPOINTMENT".equals(entityType)) {
            if (actual == null) {
                return "Không tìm thấy hồ sơ lịch hẹn tương ứng với thao tác " + latest.getEventType();
            }
            var apptOpt = appointmentRepository.findById(key.entityId());
            if (apptOpt.isPresent()) {
                var appt = apptOpt.get();
                String apptCode = appt.getAppointmentCode();
                String patientName = appt.getPatientProfile() != null ? appt.getPatientProfile().getFullName()
                        : (appt.getPatient() != null ? appt.getPatient().getFullName() : null);
                if (patientName != null && !patientName.isBlank()) {
                    return "Lệch trạng thái lịch hẹn " + apptCode + " (Bệnh nhân: " + patientName + "): Thực tế là "
                            + actual + ", nhật ký ghi nhận " + latest.getNextStatus();
                }
                return "Lệch trạng thái lịch hẹn " + apptCode + ": Thực tế là " + actual + ", nhật ký ghi nhận "
                        + latest.getNextStatus();
            }
            return "Lệch trạng thái lịch hẹn: Thực tế là " + actual + ", nhật ký ghi nhận " + latest.getNextStatus();
        } else if ("QUEUE_TICKET".equals(entityType)) {
            if (actual == null) {
                return "Không tìm thấy phiếu hàng đợi tương ứng với thao tác " + latest.getEventType();
            }
            var ticketOpt = queueTicketRepository.findById(key.entityId());
            if (ticketOpt.isPresent()) {
                var ticket = ticketOpt.get();
                int queueNum = ticket.getQueueNumber();
                String apptCode = ticket.getAppointment() != null ? ticket.getAppointment().getAppointmentCode() : null;
                if (apptCode != null && !apptCode.isBlank()) {
                    return "Lệch trạng thái phiếu khám STT " + queueNum + " (Lịch hẹn: " + apptCode + "): Thực tế là "
                            + actual + ", nhật ký ghi nhận " + latest.getNextStatus();
                }
                return "Lệch trạng thái phiếu khám STT " + queueNum + ": Thực tế là " + actual + ", nhật ký ghi nhận "
                        + latest.getNextStatus();
            }
            return "Lệch trạng thái phiếu khám: Thực tế là " + actual + ", nhật ký ghi nhận " + latest.getNextStatus();
        } else if ("EXAMINATION".equals(entityType)) {
            if (actual == null) {
                return "Không tìm thấy phiên khám tương ứng với thao tác " + latest.getEventType();
            }
            return "Lệch trạng thái phiên khám: Thực tế là " + actual + ", nhật ký ghi nhận " + latest.getNextStatus();
        } else {
            if (actual == null) {
                return "Không tìm thấy dữ liệu " + key.entityType() + " cho thao tác nghiệp vụ " + latest.getEventType();
            }
            return "Trạng thái hiện tại " + actual + " không khớp nhật ký " + latest.getNextStatus();
        }
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

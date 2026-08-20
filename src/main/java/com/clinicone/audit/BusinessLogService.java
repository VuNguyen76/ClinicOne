package com.clinicone.audit;

import lombok.Builder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import com.clinicone.reconciliation.ReconciliationStatus;
import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BusinessLogService {
    private final BusinessLogRepository repository;
    private final ReconciliationIncidentRepository reconciliationRepository;
    private final AppointmentRepository appointmentRepository;

    @Builder
    public BusinessLogService(BusinessLogRepository repository,
                              ReconciliationIncidentRepository reconciliationRepository,
                              AppointmentRepository appointmentRepository) {
        this.repository = repository;
        this.reconciliationRepository = reconciliationRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Records a real state transition. No entry is created when the state did
     * not change, which keeps retries idempotent and the journal meaningful.
     */
    @Transactional
    public void recordTransition(UUID eventId, String entityType, UUID entityId, String previousStatus,
                                 String nextStatus, String eventType, String actor, String reason) {
        if (eventId == null || entityType == null || entityId == null || nextStatus == null || eventType == null) {
            throw new IllegalArgumentException("Business log requires event, entity, next state and event type");
        }
        if (sameState(previousStatus, nextStatus)) {
            return;
        }
        recordActivity(eventId, entityType, entityId, previousStatus, nextStatus, eventType, actor, reason);
    }

    /** Records an operational adjustment even when the lifecycle status is unchanged. */
    @Transactional
    public void recordActivity(UUID eventId, String entityType, UUID entityId, String previousStatus,
                               String nextStatus, String eventType, String actor, String reason) {
        if (eventId == null || entityType == null || entityId == null || nextStatus == null || eventType == null) {
            throw new IllegalArgumentException("Business log requires event, entity, next state and event type");
        }
        if (reconciliationRepository != null
                && reconciliationRepository.existsByEntityTypeAndEntityIdAndStatus(
                entityType, entityId, ReconciliationStatus.OPEN)) {
            throw new AuthException(HttpStatus.CONFLICT, "RECONCILIATION_REQUIRED",
                    "Đối tượng đang có sự cố cần đối soát trước khi thực hiện tiếp.");
        }
        String normalizedReason = normalizeReason(reason);
        if (repository.existsByEventIdAndEntityTypeAndEntityId(eventId, entityType, entityId)) {
            return;
        }
        BusinessLog log = BusinessLog.transition(eventId, normalize(entityType, 40), entityId,
                normalizeNullable(previousStatus, 40), normalize(nextStatus, 40), normalize(eventType, 80),
                normalizeActor(actor), normalizedReason);
        String previousHash = repository.findTopByOrderByOccurredAtDescIdDesc()
                .map(BusinessLog::getHash)
                .orElse(null);
        log.seal(previousHash);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<BusinessLogResponse> list(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(entityType, entityId).stream()
                .limit(100)
                .map(BusinessLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessLogPageResponse page(String entityType, UUID entityId, int page, int size) {
        if (entityType == null || entityType.isBlank() || entityId == null
                || page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUDIT_PAGE_INVALID",
                    "Cần chọn đối tượng hợp lệ; trang từ 0 và kích thước mỗi trang từ 1 đến 100 bản ghi.");
        }
        Page<BusinessLog> result = repository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                normalize(entityType, 40), entityId, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public BusinessLogPageResponse search(String entityType, String identifier, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "AUDIT_PAGE_INVALID",
                    "Cần chọn đối tượng hợp lệ; trang từ 0 và kích thước mỗi trang từ 1 đến 100 bản ghi.");
        }
        String cleanType = (entityType == null || entityType.isBlank()) ? null : entityType.trim();
        String cleanIdentifier = (identifier == null || identifier.isBlank()) ? null : identifier.trim();
        PageRequest pageable = PageRequest.of(page, size);

        if (cleanIdentifier == null) {
            Page<BusinessLog> result = cleanType != null
                    ? repository.findByEntityTypeOrderByOccurredAtDescIdDesc(normalize(cleanType, 40), pageable)
                    : repository.findAllByOrderByOccurredAtDescIdDesc(pageable);
            return toPageResponse(result);
        }

        UUID entityId = null;
        String resolvedEntityType = cleanType;

        try {
            entityId = UUID.fromString(cleanIdentifier);
        } catch (IllegalArgumentException ignored) {
            if (appointmentRepository != null) {
                Optional<Appointment> appointment = appointmentRepository.findByAppointmentCode(cleanIdentifier);
                if (appointment.isPresent()) {
                    entityId = appointment.get().getId();
                    if (resolvedEntityType == null) {
                        resolvedEntityType = "APPOINTMENT";
                    }
                }
            }
        }

        if (entityId == null) {
            return new BusinessLogPageResponse(List.of(), page, size, 0, 0, true);
        }

        Page<BusinessLog> result;
        if (resolvedEntityType != null) {
            result = repository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
                    normalize(resolvedEntityType, 40), entityId, pageable);
        } else {
            result = repository.findByEntityIdOrderByOccurredAtDescIdDesc(entityId, pageable);
        }
        return toPageResponse(result);
    }

    private BusinessLogPageResponse toPageResponse(Page<BusinessLog> result) {
        return new BusinessLogPageResponse(
                result.getContent().stream().map(BusinessLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    private boolean sameState(String previousStatus, String nextStatus) {
        return previousStatus != null && previousStatus.equals(nextStatus);
    }

    private String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "SYSTEM" : normalize(actor, 120);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        return normalize(reason, 500);
    }

    private String normalizeNullable(String value, int maxLength) {
        return value == null || value.isBlank() ? null : normalize(value, maxLength);
    }

    private String normalize(String value, int maxLength) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Business log field is invalid");
        }
        return normalized;
    }
}

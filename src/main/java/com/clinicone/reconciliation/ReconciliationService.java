package com.clinicone.reconciliation;

import com.clinicone.audit.BusinessLogRepository;
import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReconciliationService {
    private final ReconciliationIncidentRepository repository;
    private final BusinessLogRepository businessLogRepository;
    private final Clock clock;
    private final ReconciliationActionDispatcher actionDispatcher;

    public ReconciliationService(ReconciliationIncidentRepository repository,
                                 BusinessLogRepository businessLogRepository, Clock clock) {
        this(repository, businessLogRepository, clock, (incident, request, actor) -> { });
    }

    @Autowired
    public ReconciliationService(ReconciliationIncidentRepository repository,
                                 BusinessLogRepository businessLogRepository, Clock clock,
                                 ReconciliationActionDispatcher actionDispatcher) {
        this.repository = repository;
        this.businessLogRepository = businessLogRepository;
        this.clock = clock;
        this.actionDispatcher = actionDispatcher;
    }

    @Transactional
    public ReconciliationResponse open(OpenReconciliationRequest request) {
        String entityType = normalize(request.entityType(), 40);
        String reason = normalize(request.reason(), 500);
        String assignee = normalize(request.assignee(), 120);
        ReconciliationIncident incident = ReconciliationIncident.open(nextCode(), entityType, request.entityId(),
                request.eventId(), reason, assignee);
        return ReconciliationResponse.from(repository.save(incident));
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResponse> list(ReconciliationStatus status) {
        ReconciliationStatus requested = status == null ? ReconciliationStatus.OPEN : status;
        return repository.findByStatusOrderByCreatedAtDesc(requested).stream()
                .map(ReconciliationResponse::from)
                .toList();
    }

    @Transactional
    public ReconciliationResponse close(UUID id, CloseReconciliationRequest request, String closer) {
        ReconciliationIncident incident = repository.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (incident.getStatus() != ReconciliationStatus.OPEN) {
            throw conflict("RECONCILIATION_ALREADY_CLOSED", "Đối soát này đã được đóng.");
        }
        String referenceValue = normalize(request.referenceValue(), 120);
        validateReference(request.referenceType(), referenceValue, incident);
        if (request.action() != ReconciliationAction.NO_ACTION_REQUIRED
                && request.referenceType() != ReconciliationReferenceType.BUSINESS_LOG) {
            throw badRequest("RECONCILIATION_REPLAY_REFERENCE_REQUIRED",
                    "Hành động chạy lại phải gắn với mã nhật ký nghiệp vụ.");
        }
        String resultNote = normalize(request.resultNote(), 500);
        if (resultNote.length() < 10) {
            throw badRequest("RECONCILIATION_RESULT_REQUIRED", "Ghi chú kết quả phải có từ 10 đến 500 ký tự.");
        }
        String normalizedCloser = normalize(closer, 120);
        incident.close(request.action(), request.referenceType(), referenceValue, resultNote, normalizedCloser,
                Instant.now(clock));
        actionDispatcher.dispatch(incident, request, normalizedCloser);
        return ReconciliationResponse.from(repository.save(incident));
    }

    private void validateReference(ReconciliationReferenceType type, String value,
                                   ReconciliationIncident incident) {
        if (type == ReconciliationReferenceType.INCIDENT) {
            if (!incident.getIncidentCode().equalsIgnoreCase(value)) {
                throw badRequest("RECONCILIATION_REFERENCE_INVALID", "Mã sự cố tham chiếu không khớp đối soát.");
            }
            return;
        }
        try {
            UUID logId = UUID.fromString(value);
            var log = businessLogRepository.findById(logId).orElse(null);
            if (log == null) {
                throw badRequest("RECONCILIATION_LOG_NOT_FOUND", "Không tìm thấy nhật ký nghiệp vụ được tham chiếu.");
            }
            if (!log.getEntityType().equalsIgnoreCase(incident.getEntityType())
                    || !log.getEntityId().equals(incident.getEntityId())
                    || (incident.getEventId() != null && !incident.getEventId().equals(log.getEventId()))) {
                throw badRequest("RECONCILIATION_REFERENCE_MISMATCH",
                        "Nhật ký được tham chiếu không thuộc đúng hồ sơ đối soát.");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("RECONCILIATION_LOG_INVALID", "Mã nhật ký nghiệp vụ không hợp lệ.");
        }
    }

    private String nextCode() {
        return "INC-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                + "-" + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw badRequest("RECONCILIATION_FIELD_INVALID", "Thông tin đối soát không hợp lệ.");
        }
        return value.trim();
    }

    private AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RECONCILIATION_NOT_FOUND", "Không tìm thấy đối soát.");
    }
}

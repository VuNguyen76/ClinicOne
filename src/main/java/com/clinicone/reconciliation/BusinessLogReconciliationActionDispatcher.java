package com.clinicone.reconciliation;

import com.clinicone.audit.BusinessLogService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The common correction action is recorded as a real business activity. If
 * recording fails, the surrounding close transaction is rolled back and the
 * incident remains open for a safe retry.
 */
@Component
public class BusinessLogReconciliationActionDispatcher implements ReconciliationActionDispatcher {
    private final BusinessLogService businessLogService;

    public BusinessLogReconciliationActionDispatcher(BusinessLogService businessLogService) {
        this.businessLogService = businessLogService;
    }

    @Override
    public void dispatch(ReconciliationIncident incident, CloseReconciliationRequest request, String actor) {
        businessLogService.recordActivity(UUID.randomUUID(), incident.getEntityType(), incident.getEntityId(),
                null, "RECONCILED", "RECONCILIATION_" + request.action().name(), actor,
                request.resultNote());
    }
}

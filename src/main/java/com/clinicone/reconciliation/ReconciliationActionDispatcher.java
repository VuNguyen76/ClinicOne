package com.clinicone.reconciliation;

/** Executes the selected correction before a reconciliation can be closed. */
public interface ReconciliationActionDispatcher {
    void dispatch(ReconciliationIncident incident, CloseReconciliationRequest request, String actor);
}

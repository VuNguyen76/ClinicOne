package com.clinicone.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OpenReconciliationRequest(
        @NotBlank String entityType,
        UUID entityId,
        UUID eventId,
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank @Size(max = 120) String assignee
) {
    public OpenReconciliationRequest {
        if (entityId == null) {
            entityId = UUID.randomUUID();
        }
    }
}

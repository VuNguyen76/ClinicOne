package com.clinicone.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloseReconciliationRequest(
        @NotNull ReconciliationAction action,
        @NotNull ReconciliationReferenceType referenceType,
        @NotBlank @Size(max = 120) String referenceValue,
        @NotBlank @Size(min = 10, max = 500) String resultNote
) {
}

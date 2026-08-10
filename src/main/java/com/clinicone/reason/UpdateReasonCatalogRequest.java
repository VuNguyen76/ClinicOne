package com.clinicone.reason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateReasonCatalogRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 160) String label) {
}

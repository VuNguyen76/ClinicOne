package com.clinicone.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicInvoiceLookupRequest(@NotBlank @Size(max = 36) String invoiceCode) {
}

package com.clinicone.invoice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkInvoicePaidRequest(@NotNull @Positive Long amount,
                                     @NotNull PaymentMethod paymentMethod) {
}

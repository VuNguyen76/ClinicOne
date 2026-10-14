package com.clinicone.medication;

import jakarta.validation.constraints.NotNull;

public record SetCatalogActiveRequest(@NotNull Boolean active) {
}

package com.clinicone.reason;

import java.util.UUID;

public record ReasonCatalogResponse(UUID id, ReasonCatalogType type, String code, String label, boolean active) {
    public static ReasonCatalogResponse from(ReasonCatalog reason) {
        return new ReasonCatalogResponse(reason.getId(), reason.getType(), reason.getCode(), reason.getLabel(), reason.isActive());
    }
}

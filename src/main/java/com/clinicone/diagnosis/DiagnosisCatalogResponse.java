package com.clinicone.diagnosis;

import java.util.UUID;

public record DiagnosisCatalogResponse(UUID id, String code, String name, boolean active) {
    static DiagnosisCatalogResponse from(DiagnosisCatalog diagnosis) {
        return new DiagnosisCatalogResponse(diagnosis.getId(), diagnosis.getCode(), diagnosis.getName(), diagnosis.isActive());
    }
}

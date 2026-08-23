package com.clinicone.examination;

import java.time.Instant;
import java.util.UUID;

public record MedicalRecordTemplateResponse(UUID id, String code, String name, String specialty,
                                            UUID clinicServiceId, String description, String fieldDefinition,
                                            boolean active, String createdBy, Instant updatedAt) {
    public static MedicalRecordTemplateResponse from(MedicalRecordTemplate template) {
        return new MedicalRecordTemplateResponse(template.getId(), template.getCode(), template.getName(),
                template.getSpecialty(), template.getClinicServiceId(), template.getDescription(),
                template.getFieldDefinition(), template.isActive(), template.getCreatedBy(), template.getUpdatedAt());
    }
}

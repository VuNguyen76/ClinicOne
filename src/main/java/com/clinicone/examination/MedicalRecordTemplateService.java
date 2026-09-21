package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicalRecordTemplateService {
    private final MedicalRecordTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<MedicalRecordTemplateResponse> list(boolean activeOnly, String specialty, UUID clinicServiceId) {
        String normalizedSpecialty = StringUtils.hasText(specialty) ? specialty.trim() : null;
        return (activeOnly ? repository.findAllByActiveTrueOrderBySpecialtyAscNameAsc()
                : repository.findAllByOrderBySpecialtyAscNameAsc()).stream()
                .filter(template -> normalizedSpecialty == null
                        || template.getSpecialty().equalsIgnoreCase(normalizedSpecialty))
                .filter(template -> clinicServiceId == null || template.getClinicServiceId() == null
                        || clinicServiceId.equals(template.getClinicServiceId()))
                .map(MedicalRecordTemplateResponse::from)
                .toList();
    }

    @Transactional
    public MedicalRecordTemplateResponse create(MedicalRecordTemplateRequest request, String actor) {
        String code = normalize(request.code(), 40);
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICAL_TEMPLATE_CODE_EXISTS", "Mã mẫu phiếu đã tồn tại.");
        }
        return MedicalRecordTemplateResponse.from(repository.save(MedicalRecordTemplate.create(code,
                normalize(request.name(), 160), normalize(request.specialty(), 120), request.clinicServiceId(),
                optional(request.description(), 500), normalize(request.fieldDefinition(), 20000), normalize(actor, 120))));
    }

    @Transactional
    public MedicalRecordTemplateResponse update(UUID id, MedicalRecordTemplateRequest request) {
        MedicalRecordTemplate template = repository.findById(id).orElseThrow(this::notFound);
        template.update(normalize(request.name(), 160), normalize(request.specialty(), 120), request.clinicServiceId(),
                optional(request.description(), 500), normalize(request.fieldDefinition(), 20000));
        return MedicalRecordTemplateResponse.from(template);
    }

    @Transactional
    public void deactivate(UUID id) {
        MedicalRecordTemplate template = repository.findById(id).orElseThrow(this::notFound);
        template.deactivate();
    }

    private String normalize(String value, int max) {
        if (!StringUtils.hasText(value) || value.trim().length() > max) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_TEMPLATE_FIELD_INVALID", "Thông tin mẫu phiếu không hợp lệ.");
        }
        return value.trim();
    }

    private String optional(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        if (value.trim().length() > max) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_TEMPLATE_FIELD_INVALID", "Thông tin mẫu phiếu không hợp lệ.");
        }
        return value.trim();
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "MEDICAL_TEMPLATE_NOT_FOUND", "Không tìm thấy mẫu phiếu.");
    }
}

package com.clinicone.reason;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reasons")
public class ReasonCatalogController {
    private final ReasonCatalogService service;
    private final ClinicalReasonRepository clinicalReasonRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'COORDINATOR', 'ADMIN')")
    public List<ReasonCatalogResponse> list(@RequestParam(defaultValue = "APPOINTMENT_CANCELLATION") ReasonCatalogType type) {
        return service.list(type, true);
    }

    @GetMapping("/clinical")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'COORDINATOR', 'ADMIN')")
    public List<ClinicalReasonResponse> clinicalReasons(
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        if (specialty != null && !specialty.isBlank()) {
            return clinicalReasonRepository.findBySpecialtyNameIgnoreCaseAndActiveTrueOrderBySortOrderAsc(specialty.trim())
                    .stream()
                    .map(ClinicalReasonResponse::from)
                    .toList();
        }
        return activeOnly
                ? clinicalReasonRepository.findByActiveTrueOrderBySpecialtyNameAscSortOrderAsc()
                .stream()
                .map(ClinicalReasonResponse::from)
                .toList()
                : clinicalReasonRepository.findAllByOrderBySpecialtyNameAscSortOrderAsc()
                .stream()
                .map(ClinicalReasonResponse::from)
                .toList();
    }

    @PostMapping("/clinical")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<ClinicalReasonResponse> createClinicalReason(
            @Valid @RequestBody CreateClinicalReasonRequest request) {
        if (clinicalReasonRepository.existsBySpecialtyNameIgnoreCaseAndSymptomNameIgnoreCase(
                request.specialtyName(), request.symptomName())) {
            throw new com.clinicone.auth.AuthException(org.springframework.http.HttpStatus.CONFLICT,
                    "CLINICAL_REASON_EXISTS", "Lý do khám này đã tồn tại trong chuyên khoa.");
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        ClinicalReason reason = new ClinicalReason(request.specialtyCode(), request.specialtyName(),
                request.symptomName(), sortOrder);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ClinicalReasonResponse.from(clinicalReasonRepository.save(reason)));
    }

    @org.springframework.web.bind.annotation.PutMapping("/clinical/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<ClinicalReasonResponse> updateClinicalReason(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClinicalReasonRequest request) {
        ClinicalReason reason = clinicalReasonRepository.findById(id)
                .orElseThrow(() -> new com.clinicone.auth.AuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "CLINICAL_REASON_NOT_FOUND", "Không tìm thấy lý do khám."));
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : reason.getSortOrder();
        reason.update(request.specialtyCode(), request.specialtyName(), request.symptomName(), sortOrder);
        return ResponseEntity.ok(ClinicalReasonResponse.from(clinicalReasonRepository.save(reason)));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/clinical/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<Void> deleteClinicalReason(@PathVariable UUID id) {
        ClinicalReason reason = clinicalReasonRepository.findById(id)
                .orElseThrow(() -> new com.clinicone.auth.AuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "CLINICAL_REASON_NOT_FOUND", "Không tìm thấy lý do khám."));
        clinicalReasonRepository.delete(reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clinical/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<ClinicalReasonResponse> toggleActive(@PathVariable UUID id) {
        ClinicalReason reason = clinicalReasonRepository.findById(id)
                .orElseThrow(() -> new com.clinicone.auth.AuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "CLINICAL_REASON_NOT_FOUND", "Không tìm thấy lý do khám."));
        reason.setActive(!reason.isActive());
        return ResponseEntity.ok(ClinicalReasonResponse.from(clinicalReasonRepository.save(reason)));
    }
}

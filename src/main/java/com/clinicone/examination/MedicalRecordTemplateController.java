package com.clinicone.examination;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/medical-record-templates")
public class MedicalRecordTemplateController {
    private final MedicalRecordTemplateService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'COORDINATOR', 'ADMIN')")
    public List<MedicalRecordTemplateResponse> list(Authentication authentication,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) UUID clinicServiceId) {
        boolean doctor = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DOCTOR".equals(authority.getAuthority()));
        return service.list(activeOnly || doctor, specialty, clinicServiceId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<MedicalRecordTemplateResponse> create(Authentication authentication,
                                                                 @Valid @RequestBody MedicalRecordTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public MedicalRecordTemplateResponse update(@PathVariable UUID id,
                                                @Valid @RequestBody MedicalRecordTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

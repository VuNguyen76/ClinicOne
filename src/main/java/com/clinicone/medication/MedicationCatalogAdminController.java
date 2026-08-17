package com.clinicone.medication;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/medications")
@PreAuthorize("hasRole('ADMIN')")
public class MedicationCatalogAdminController {
    private final MedicationCatalogService service;

    @GetMapping
    public List<MedicationResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationResponse create(@Valid @RequestBody CreateMedicationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public MedicationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMedicationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public MedicationResponse setActive(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setActive(id, value);
    }
}

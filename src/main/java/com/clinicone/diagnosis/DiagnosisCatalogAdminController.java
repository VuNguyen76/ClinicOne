package com.clinicone.diagnosis;

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
@RequestMapping("/api/v1/admin/diagnoses")
@PreAuthorize("hasRole('ADMIN')")
public class DiagnosisCatalogAdminController {
    private final DiagnosisCatalogService service;

    public DiagnosisCatalogAdminController(DiagnosisCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<DiagnosisCatalogResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosisCatalogResponse create(@Valid @RequestBody CreateDiagnosisCatalogRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DiagnosisCatalogResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDiagnosisCatalogRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public DiagnosisCatalogResponse setActive(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setActive(id, value);
    }
}

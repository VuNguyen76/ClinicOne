package com.clinicone.reason;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/admin/reason-catalog")
public class ReasonCatalogAdminController {
    private final ReasonCatalogService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public List<ReasonCatalogResponse> list(@RequestParam(defaultValue = "APPOINTMENT_CANCELLATION") ReasonCatalogType type,
                                            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(type, activeOnly);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReasonCatalogResponse> create(@Valid @RequestBody CreateReasonCatalogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReasonCatalogResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateReasonCatalogRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ReasonCatalogResponse activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ReasonCatalogResponse deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }
}

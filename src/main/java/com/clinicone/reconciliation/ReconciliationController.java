package com.clinicone.reconciliation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/admin/reconciliations")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class ReconciliationController {
    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReconciliationResponse> list(@RequestParam(required = false) ReconciliationStatus status) {
        return service.list(status);
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<ReconciliationResponse> open(@Valid @RequestBody OpenReconciliationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.open(request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ReconciliationResponse close(Authentication authentication, @PathVariable UUID id,
                                        @Valid @RequestBody CloseReconciliationRequest request) {
        return service.close(id, request, authentication.getName());
    }
}

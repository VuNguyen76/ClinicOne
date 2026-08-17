package com.clinicone.schedule;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/services")
public class ClinicServiceController {
    private final ClinicServiceManagementService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<ClinicServiceResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<ClinicServiceResponse>> listActive() {
        return ResponseEntity.ok(service.listActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<ClinicServiceResponse> create(@Valid @RequestBody CreateClinicServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<ClinicServiceResponse> update(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateClinicServiceRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<ClinicServiceResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<ClinicServiceResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.setActive(id, false));
    }
}

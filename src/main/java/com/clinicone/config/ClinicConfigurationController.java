package com.clinicone.config;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/configuration")
@PreAuthorize("hasRole('ADMIN')")
public class ClinicConfigurationController {
    private final ClinicConfigurationService service;

    @GetMapping
    public ResponseEntity<ClinicConfigurationResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<ClinicConfigurationResponse> update(Authentication authentication,
                                                               @Valid @RequestBody UpdateClinicConfigurationRequest request) {
        return ResponseEntity.ok(service.update(request, authentication.getName()));
    }
}

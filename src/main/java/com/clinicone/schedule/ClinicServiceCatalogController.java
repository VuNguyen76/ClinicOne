package com.clinicone.schedule;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public read model used by the patient booking flow. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/services")
public class ClinicServiceCatalogController {
    private final ClinicServiceManagementService serviceManagement;

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'COORDINATOR', 'ADMIN')")
    public List<ClinicServiceResponse> listActive() {
        return serviceManagement.listActive();
    }
}

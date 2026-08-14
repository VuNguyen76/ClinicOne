package com.clinicone.reason;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reasons")
public class ReasonCatalogController {
    private final ReasonCatalogService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'COORDINATOR', 'ADMIN')")
    public List<ReasonCatalogResponse> list(@RequestParam(defaultValue = "APPOINTMENT_CANCELLATION") ReasonCatalogType type) {
        return service.list(type, true);
    }
}

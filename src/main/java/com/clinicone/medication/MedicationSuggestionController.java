package com.clinicone.medication;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctor/medications")
@PreAuthorize("hasRole('DOCTOR')")
public class MedicationSuggestionController {
    private final MedicationCatalogService service;

    public MedicationSuggestionController(MedicationCatalogService service) {
        this.service = service;
    }

    @GetMapping("/suggestions")
    public List<MedicationResponse> suggestions(@RequestParam String query) {
        return service.suggestions(query);
    }
}

package com.clinicone.medication;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/doctor/medications")
@PreAuthorize("hasRole('DOCTOR')")
public class MedicationSuggestionController {
    private final MedicationCatalogService service;

    @GetMapping("/suggestions")
    public List<MedicationResponse> suggestions(@RequestParam String query) {
        return service.suggestions(query);
    }
}

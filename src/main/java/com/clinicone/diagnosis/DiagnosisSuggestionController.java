package com.clinicone.diagnosis;

import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctor/diagnoses")
@PreAuthorize("hasRole('DOCTOR')")
public class DiagnosisSuggestionController {
    private final DiagnosisCatalogService service;

    public DiagnosisSuggestionController(DiagnosisCatalogService service) {
        this.service = service;
    }

    @GetMapping("/suggestions")
    public List<DiagnosisCatalogResponse> suggestions(@RequestParam @Size(max = 100) String query) {
        return service.suggestions(query);
    }
}

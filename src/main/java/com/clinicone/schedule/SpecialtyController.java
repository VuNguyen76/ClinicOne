package com.clinicone.schedule;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specialties")
public class SpecialtyController {
    private final SpecialtyCatalogService specialtyCatalog;

    public SpecialtyController(SpecialtyCatalogService specialtyCatalog) {
        this.specialtyCatalog = specialtyCatalog;
    }

    @GetMapping
    public ResponseEntity<List<SpecialtyResponse>> list(
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(specialtyCatalog.list(query));
    }
}

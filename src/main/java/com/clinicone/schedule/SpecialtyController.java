package com.clinicone.schedule;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<SpecialtyResponse> create(@Valid @org.springframework.web.bind.annotation.RequestBody CreateSpecialtyRequest request) {
        return ResponseEntity.status(201).body(specialtyCatalog.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public SpecialtyResponse update(@PathVariable String code,
                                    @Valid @org.springframework.web.bind.annotation.RequestBody CreateSpecialtyRequest request) {
        return specialtyCatalog.update(code, request);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        specialtyCatalog.deactivate(code);
        return ResponseEntity.noContent().build();
    }
}

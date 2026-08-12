package com.clinicone.schedule;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/schedule-templates")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class ScheduleTemplateController {
    private final ScheduleTemplateService service;

    public ScheduleTemplateController(ScheduleTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleTemplateResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<ScheduleTemplateResponse> create(
            @Valid @RequestBody CreateScheduleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<ScheduleTemplateResponse> regenerate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.regenerate(id));
    }
}

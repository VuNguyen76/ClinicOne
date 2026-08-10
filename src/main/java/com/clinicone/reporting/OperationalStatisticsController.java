package com.clinicone.reporting;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class OperationalStatisticsController {
    private final OperationalStatisticsService service;

    public OperationalStatisticsController(OperationalStatisticsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<OperationalStatisticsResponse> summarize(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String specialty,
            @RequestParam(required = false) UUID doctorId) {
        return ResponseEntity.ok(service.summarize(from, to, specialty, doctorId));
    }
}

package com.clinicone.rescheduling;

import lombok.RequiredArgsConstructor;

import com.clinicone.schedule.AvailableSlotResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rescheduling")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class ReschedulingController {
    private final ReschedulingService service;

    @GetMapping
    public ResponseEntity<List<RescheduleCaseResponse>> listOpen() {
        return ResponseEntity.ok(service.listOpen());
    }

    @GetMapping("/{caseId}/alternatives")
    public ResponseEntity<List<AvailableSlotResponse>> alternatives(
            @PathVariable UUID caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.alternatives(caseId, from, to));
    }

    @PostMapping("/{caseId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<RescheduleCaseResponse> resolve(Authentication authentication,
                                                           @PathVariable UUID caseId,
                                                           @Valid @RequestBody ResolveRescheduleRequest request) {
        return ResponseEntity.ok(service.resolve(caseId, request, authentication.getName()));
    }
}

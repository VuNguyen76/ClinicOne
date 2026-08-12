package com.clinicone.rescheduling;

import com.clinicone.schedule.AvailableSlotResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/patient/rescheduling")
@PreAuthorize("hasRole('PATIENT')")
public class PatientReschedulingController {
    private final ReschedulingService service;

    public PatientReschedulingController(ReschedulingService service) {
        this.service = service;
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<RescheduleCaseResponse> find(Authentication authentication,
                                                        @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(service.findForPatient(authentication.getName(), appointmentId));
    }

    @GetMapping("/{appointmentId}/alternatives")
    public ResponseEntity<List<AvailableSlotResponse>> alternatives(
            Authentication authentication,
            @PathVariable UUID appointmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.alternativesForPatient(authentication.getName(), appointmentId, from, to));
    }

    @PostMapping("/{appointmentId}/confirm")
    public ResponseEntity<RescheduleCaseResponse> confirm(Authentication authentication,
                                                          @PathVariable UUID appointmentId,
                                                          @Valid @RequestBody ResolveRescheduleRequest request) {
        return ResponseEntity.ok(service.resolveForPatient(authentication.getName(), appointmentId, request));
    }
}

package com.clinicone.examination;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctor/examinations")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorExaminationController {
    private final DoctorExaminationService service;

    public DoctorExaminationController(DoctorExaminationService service) {
        this.service = service;
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<DoctorExaminationResponse> open(Authentication authentication,
                                                          @PathVariable UUID ticketId) {
        return ResponseEntity.ok(service.open(ticketId, authentication.getName()));
    }

    @PutMapping("/{ticketId}/draft")
    public ResponseEntity<DoctorExaminationResponse> saveDraft(Authentication authentication,
                                                                @PathVariable UUID ticketId,
                                                                @Valid @RequestBody DoctorExaminationRequest request) {
        return ResponseEntity.ok(service.saveDraft(ticketId, authentication.getName(), request));
    }

    @PostMapping("/{ticketId}/sign")
    public ResponseEntity<DoctorExaminationResponse> sign(Authentication authentication,
                                                           @PathVariable UUID ticketId,
                                                           @Valid @RequestBody DoctorExaminationRequest request) {
        return ResponseEntity.ok(service.sign(ticketId, authentication.getName(), request));
    }
}

package com.clinicone.examination;

import com.clinicone.audit.AccessAuditService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final ObjectProvider<AccessAuditService> accessAudit;

    public DoctorExaminationController(DoctorExaminationService service,
                                       ObjectProvider<AccessAuditService> accessAudit) {
        this.service = service;
        this.accessAudit = accessAudit;
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<DoctorExaminationResponse> open(Authentication authentication,
                                                          @PathVariable UUID ticketId,
                                                          HttpServletRequest request) {
        try {
            DoctorExaminationResponse response = service.open(ticketId, authentication.getName());
            recordAudit("DOCTOR_VIEW_EXAMINATION", authentication.getName(), "SUCCESS",
                    request.getRequestURI(), request.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (RuntimeException exception) {
            recordAudit("DOCTOR_VIEW_EXAMINATION", authentication.getName(), "FAILED",
                    request.getRequestURI(), request.getRemoteAddr());
            throw exception;
        }
    }

    @PostMapping("/{ticketId}/start")
    public ResponseEntity<DoctorExaminationResponse> start(Authentication authentication,
                                                            @PathVariable UUID ticketId,
                                                            @RequestHeader(value = "Idempotency-Key", required = false)
                                                            String requestKey) {
        return ResponseEntity.ok(service.start(ticketId, authentication.getName(), requestKey));
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
                                                           @RequestHeader("Idempotency-Key") String requestKey,
                                                           @Valid @RequestBody DoctorExaminationRequest request) {
        return ResponseEntity.ok(service.sign(ticketId, authentication.getName(), request, requestKey));
    }

    @PostMapping("/{ticketId}/stop")
    public ResponseEntity<DoctorExaminationResponse> stop(Authentication authentication,
                                                           @PathVariable UUID ticketId,
                                                           @Valid @RequestBody StopExaminationRequest request) {
        return ResponseEntity.ok(service.stop(ticketId, authentication.getName(), request));
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        AccessAuditService service = accessAudit.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.record(eventType, actor, outcome, function, ipAddress);
        } catch (RuntimeException ignored) {
            // Clinical workspace must remain available if the audit store is unavailable.
        }
    }
}

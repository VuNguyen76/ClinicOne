package com.clinicone.appointment;

import com.clinicone.audit.AccessAuditService;
import com.clinicone.auth.AuthenticatedIds;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/doctor/appointments")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorAppointmentsController {
    private final DoctorAppointmentsService service;
    private final ObjectProvider<AccessAuditService> accessAudit;

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        String principal = authentication.getName();
        try {
            var appointments = service.list(AuthenticatedIds.doctor(principal), from, to);
            recordAudit("DOCTOR_VIEW_OWN_APPOINTMENTS", principal, "SUCCESS", request);
            return ResponseEntity.ok(appointments);
        } catch (RuntimeException exception) {
            recordAudit("DOCTOR_VIEW_OWN_APPOINTMENTS", principal, "FAILED", request);
            throw exception;
        }
    }

    private void recordAudit(String eventType, String actor, String outcome, HttpServletRequest request) {
        AccessAuditService auditService = accessAudit.getIfAvailable();
        if (auditService == null) return;
        try {
            auditService.record(eventType, actor, outcome, request.getRequestURI(), request.getRemoteAddr());
        } catch (RuntimeException ignored) {
            // Keep the read-only appointment workflow available if audit storage is unavailable.
        }
    }
}

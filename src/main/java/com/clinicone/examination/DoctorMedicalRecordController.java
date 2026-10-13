package com.clinicone.examination;

import com.clinicone.audit.AccessAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/doctor/medical-records")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorMedicalRecordController {
    private final DoctorMedicalRecordService service;
    private final ObjectProvider<AccessAuditService> accessAudit;

    @GetMapping
    public ResponseEntity<DoctorMedicalRecordHistoryPage> list(Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            var history = service.listHistory(authentication.getName(),
                    new DoctorMedicalRecordHistoryQuery(search, from, to, page, size));
            recordAudit("DOCTOR_VIEW_MEDICAL_RECORD_HISTORY", authentication.getName(), "SUCCESS", request);
            return ResponseEntity.ok(history);
        } catch (RuntimeException exception) {
            recordAudit("DOCTOR_VIEW_MEDICAL_RECORD_HISTORY", authentication.getName(), "FAILED", request);
            throw exception;
        }
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<DoctorMedicalRecordResponse> get(Authentication authentication,
            @PathVariable UUID recordId, HttpServletRequest request) {
        try {
            var record = service.get(authentication.getName(), recordId);
            recordAudit("DOCTOR_VIEW_MEDICAL_RECORD", authentication.getName(), "SUCCESS", request);
            return ResponseEntity.ok(record);
        } catch (RuntimeException exception) {
            recordAudit("DOCTOR_VIEW_MEDICAL_RECORD", authentication.getName(), "FAILED", request);
            throw exception;
        }
    }

    private void recordAudit(String eventType, String actor, String outcome, HttpServletRequest request) {
        AccessAuditService auditService = accessAudit.getIfAvailable();
        if (auditService == null) return;
        try {
            auditService.record(eventType, actor, outcome, request.getRequestURI(), request.getRemoteAddr());
        } catch (RuntimeException ignored) {
            // Keep read-only clinical workflows available if audit storage is temporarily unavailable.
        }
    }
}

package com.clinicone.examination;

import com.clinicone.audit.AccessAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-records")
@PreAuthorize("hasRole('PATIENT')")
public class MedicalRecordController {
    private final MedicalRecordService service;
    private final ObjectProvider<AccessAuditService> accessAudit;

    public MedicalRecordController(MedicalRecordService service,
                                   ObjectProvider<AccessAuditService> accessAudit) {
        this.service = service;
        this.accessAudit = accessAudit;
    }

    @GetMapping
    public ResponseEntity<List<MedicalRecordResponse>> list(Authentication authentication,
                                                            HttpServletRequest request) {
        try {
            List<MedicalRecordResponse> records = service.list(authentication.getName());
            recordAudit("PATIENT_VIEW_MEDICAL_RECORDS", authentication.getName(), "SUCCESS",
                    request.getRequestURI(), request.getRemoteAddr());
            return ResponseEntity.ok(records);
        } catch (RuntimeException exception) {
            recordAudit("PATIENT_VIEW_MEDICAL_RECORDS", authentication.getName(), "FAILED",
                    request.getRequestURI(), request.getRemoteAddr());
            throw exception;
        }
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<MedicalRecordResponse> get(Authentication authentication,
                                                     @PathVariable UUID recordId,
                                                     HttpServletRequest request) {
        try {
            MedicalRecordResponse record = service.get(authentication.getName(), recordId.toString());
            recordAudit("PATIENT_VIEW_MEDICAL_RECORD", authentication.getName(), "SUCCESS",
                    request.getRequestURI(), request.getRemoteAddr());
            return ResponseEntity.ok(record);
        } catch (RuntimeException exception) {
            recordAudit("PATIENT_VIEW_MEDICAL_RECORD", authentication.getName(), "FAILED",
                    request.getRequestURI(), request.getRemoteAddr());
            throw exception;
        }
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        AccessAuditService service = accessAudit.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.record(eventType, actor, outcome, function, ipAddress);
        } catch (RuntimeException ignored) {
            // Clinical read must remain available if the audit store is unavailable.
        }
    }
}

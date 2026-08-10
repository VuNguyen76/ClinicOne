package com.clinicone.auth;

import com.clinicone.audit.AccessAuditService;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffManagementController {
    private final StaffManagementService service;
    private final AccessAuditService accessAuditService;

    public StaffManagementController(StaffManagementService service, AccessAuditService accessAuditService) {
        this.service = service;
        this.accessAuditService = accessAuditService;
    }

    @GetMapping
    public ResponseEntity<List<StaffAccountResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping("/{staffId}/lock")
    public ResponseEntity<StaffAccountResponse> lock(@PathVariable @NotNull UUID staffId, Principal principal,
                                                     HttpServletRequest request) {
        StaffAccountResponse response = service.lock(staffId, principal == null ? "SYSTEM" : principal.getName());
        recordAudit("STAFF_LOCK", principal == null ? "SYSTEM" : principal.getName(), "SUCCESS",
                "/api/v1/admin/staff/{id}/lock", request.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{staffId}/unlock")
    public ResponseEntity<StaffAccountResponse> unlock(@PathVariable @NotNull UUID staffId, Principal principal,
                                                       HttpServletRequest request) {
        StaffAccountResponse response = service.unlock(staffId, principal == null ? "SYSTEM" : principal.getName());
        recordAudit("STAFF_UNLOCK", principal == null ? "SYSTEM" : principal.getName(), "SUCCESS",
                "/api/v1/admin/staff/{id}/unlock", request.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        try { accessAuditService.record(eventType, actor, outcome, function, ipAddress); }
        catch (RuntimeException ignored) { }
    }
}

package com.clinicone.auth;

import lombok.RequiredArgsConstructor;

import com.clinicone.audit.AccessAuditService;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffManagementController {
    private final StaffManagementService service;
    private final AccessAuditService accessAuditService;

    @GetMapping
    public ResponseEntity<List<StaffAccountResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<StaffAccountCreatedResponse> create(@Valid @RequestBody CreateStaffAccountRequest request,
                                                               Principal principal, HttpServletRequest httpRequest) {
        String actor = actor(principal);
        try {
            StaffAccountCreatedResponse response = service.create(request);
            recordAudit("STAFF_CREATE", actor, "SUCCESS", "/api/v1/admin/staff", httpRequest.getRemoteAddr());
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
        } catch (RuntimeException failure) {
            recordAudit("STAFF_CREATE", actor, "FAILED", "/api/v1/admin/staff", httpRequest.getRemoteAddr());
            throw failure;
        }
    }

    @PutMapping("/{staffId}/roles")
    public ResponseEntity<StaffAccountResponse> updateRoles(@PathVariable @NotNull UUID staffId,
                                                            @Valid @RequestBody UpdateStaffRolesRequest request,
                                                            Principal principal, HttpServletRequest httpRequest) {
        String actor = actor(principal);
        try {
            StaffAccountResponse response = service.updateRoles(staffId, request);
            recordAudit("STAFF_ROLE_UPDATE", actor, "SUCCESS", "/api/v1/admin/staff/{id}/roles",
                    httpRequest.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (RuntimeException failure) {
            recordAudit("STAFF_ROLE_UPDATE", actor, "FAILED", "/api/v1/admin/staff/{id}/roles",
                    httpRequest.getRemoteAddr());
            throw failure;
        }
    }

    @PostMapping("/{staffId}/lock")
    public ResponseEntity<StaffAccountResponse> lock(@PathVariable @NotNull UUID staffId, Principal principal,
                                                     HttpServletRequest request) {
        String actor = actor(principal);
        try {
            StaffAccountResponse response = service.lock(staffId, actor);
            recordAudit("STAFF_LOCK", actor, "SUCCESS", "/api/v1/admin/staff/{id}/lock", request.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (RuntimeException failure) {
            recordAudit("STAFF_LOCK", actor, "FAILED", "/api/v1/admin/staff/{id}/lock", request.getRemoteAddr());
            throw failure;
        }
    }

    @PostMapping("/{staffId}/unlock")
    public ResponseEntity<StaffAccountResponse> unlock(@PathVariable @NotNull UUID staffId, Principal principal,
                                                       HttpServletRequest request) {
        String actor = actor(principal);
        try {
            StaffAccountResponse response = service.unlock(staffId, actor);
            recordAudit("STAFF_UNLOCK", actor, "SUCCESS", "/api/v1/admin/staff/{id}/unlock",
                    request.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (RuntimeException failure) {
            recordAudit("STAFF_UNLOCK", actor, "FAILED", "/api/v1/admin/staff/{id}/unlock",
                    request.getRemoteAddr());
            throw failure;
        }
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        try { accessAuditService.record(eventType, actor, outcome, function, ipAddress); }
        catch (RuntimeException ignored) { }
    }

    private String actor(Principal principal) {
        return principal == null ? "SYSTEM" : principal.getName();
    }
}

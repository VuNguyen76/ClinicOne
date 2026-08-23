package com.clinicone.auth;

import lombok.RequiredArgsConstructor;

import com.clinicone.audit.AccessAuditService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/staff/auth")
public class StaffAuthController {
    private final StaffAuthService service;
    private final AccessAuditService accessAuditService;

    @PostMapping("/login")
    public ResponseEntity<StaffLoginResponse> login(@Valid @RequestBody StaffLoginRequest request,
                                                    HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        try {
            StaffLoginResponse response = service.login(request);
            recordAudit("STAFF_LOGIN", request.username(), "SUCCESS", "/api/v1/staff/auth/login", ipAddress);
            return ResponseEntity.ok(response);
        } catch (AuthException exception) {
            recordAudit("STAFF_LOGIN", request.username(), "FAILED", "/api/v1/staff/auth/login", ipAddress);
            throw exception;
        }
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        try {
            accessAuditService.record(eventType, actor, outcome, function, ipAddress);
        } catch (RuntimeException ignored) {
            // Access auditing must not expose credentials or make the login response fail.
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest servletRequest) {
        service.logout(authentication.getName());
        recordAudit("STAFF_LOGOUT", authentication.getName(), "SUCCESS", "/api/v1/staff/auth/logout",
                servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}

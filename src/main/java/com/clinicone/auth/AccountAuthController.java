package com.clinicone.auth;

import com.clinicone.audit.AccessAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountAuthController {

    private final AccountAuthService authService;
    private final AccessAuditService accessAuditService;

    public AccountAuthController(AccountAuthService authService, AccessAuditService accessAuditService) {
        this.authService = authService;
        this.accessAuditService = accessAuditService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @PostMapping("/login-sms")
    public ResponseEntity<LoginResponse> loginBySmsOtp(@Valid @RequestBody SmsLoginRequest request,
                                                       HttpServletRequest servletRequest) {
        try {
            LoginResponse response = authService.loginBySmsOtp(request);
            recordAudit("PATIENT_LOGIN", request.phone(), "SUCCESS", "/api/v1/auth/login-sms", servletRequest.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (AuthException exception) {
            recordAudit("PATIENT_LOGIN", request.phone(), "FAILED", "/api/v1/auth/login-sms", servletRequest.getRemoteAddr());
            throw exception;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody PasswordLoginRequest request,
                                               HttpServletRequest servletRequest) {
        try {
            LoginResponse response = authService.login(request);
            recordAudit("PATIENT_LOGIN", request.phone(), "SUCCESS", "/api/v1/auth/login", servletRequest.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (AuthException exception) {
            recordAudit("PATIENT_LOGIN", request.phone(), "FAILED", "/api/v1/auth/login", servletRequest.getRemoteAddr());
            throw exception;
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        authService.activatePendingAccount(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest servletRequest) {
        authService.logout(authentication.getName());
        recordAudit("PATIENT_LOGOUT", authentication.getName(), "SUCCESS", "/api/v1/auth/logout", servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<PatientProfileResponse> profile(Authentication authentication) {
        return ResponseEntity.ok(authService.getProfile(authentication.getName()));
    }

    @PatchMapping("/me")
    public ResponseEntity<PatientProfileResponse> updateProfile(Authentication authentication,
                                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(authentication.getName(), request));
    }

    private void recordAudit(String eventType, String actor, String outcome, String function, String ipAddress) {
        try { accessAuditService.record(eventType, actor, outcome, function, ipAddress); }
        catch (RuntimeException ignored) { }
    }
}

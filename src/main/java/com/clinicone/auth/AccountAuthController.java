package com.clinicone.auth;

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

    public AccountAuthController(AccountAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @PostMapping("/login-sms")
    public ResponseEntity<LoginResponse> loginBySmsOtp(@Valid @RequestBody SmsLoginRequest request) {
        return ResponseEntity.ok(authService.loginBySmsOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
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
}

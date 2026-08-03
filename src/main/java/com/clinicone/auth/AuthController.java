package com.clinicone.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/check-phone")
    public ResponseEntity<CheckPhoneResponse> checkPhone(
            @Valid @RequestBody CheckPhoneRequest request
    ) {
        return ResponseEntity.ok(authService.checkPhone(request.phone()));
    }
}

package com.clinicone.auth;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/check-phone")
    public ResponseEntity<CheckPhoneResponse> checkPhone(
            @Valid @RequestBody CheckPhoneRequest request
    ) {
        return ResponseEntity.ok(authService.checkPhone(request.phone()));
    }
}

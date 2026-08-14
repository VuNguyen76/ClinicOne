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
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request-sms-otp")
    public ResponseEntity<RequestOtpResponse> requestSmsOtp(@Valid @RequestBody RequestSmsOtpRequest request) {
        return ResponseEntity.ok(otpService.requestSmsOtp(request.phone(), request.purpose()));
    }

    @PostMapping("/verify-sms-otp")
    public ResponseEntity<VerifyOtpResponse> verifySmsOtp(@Valid @RequestBody VerifySmsOtpRequest request) {
        return ResponseEntity.ok(otpService.verifySmsOtp(request.phone(), request.purpose(), request.code()));
    }
}

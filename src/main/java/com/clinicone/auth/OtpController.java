package com.clinicone.auth;

import com.clinicone.validation.VietnamesePhoneNumbers;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class OtpController {

    private final OtpService otpService;
    private final PatientAccountRepository patientAccountRepository;

    @PostMapping("/request-sms-otp")
    public ResponseEntity<RequestOtpResponse> requestSmsOtp(@Valid @RequestBody RequestSmsOtpRequest request) {
        if (request.purpose() == OtpPurpose.REGISTRATION) {
            String phone = VietnamesePhoneNumbers.local(request.phone());
            Optional<PatientAccount> existing = patientAccountRepository.findByPhone(phone);
            if (existing.isPresent()) {
                if (existing.get().isMustChangePassword()) {
                    throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_PENDING_ACTIVATION",
                            "Số điện thoại này đã có tài khoản chờ kích hoạt. Vui lòng kích hoạt tài khoản hoặc đổi mật khẩu.");
                }
                throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED",
                        "Số điện thoại này đã được đăng ký tài khoản. Vui lòng đăng nhập hoặc khôi phục mật khẩu.");
            }
        }
        return ResponseEntity.ok(otpService.requestSmsOtp(request.phone(), request.purpose()));
    }

    @PostMapping("/verify-sms-otp")
    public ResponseEntity<VerifyOtpResponse> verifySmsOtp(@Valid @RequestBody VerifySmsOtpRequest request) {
        if (request.purpose() == OtpPurpose.REGISTRATION) {
            String phone = VietnamesePhoneNumbers.local(request.phone());
            Optional<PatientAccount> existing = patientAccountRepository.findByPhone(phone);
            if (existing.isPresent() && !existing.get().isMustChangePassword()) {
                throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED",
                        "Số điện thoại này đã được đăng ký tài khoản. Vui lòng đăng nhập hoặc khôi phục mật khẩu.");
            }
        }
        return ResponseEntity.ok(otpService.verifySmsOtp(request.phone(), request.purpose(), request.code()));
    }
}

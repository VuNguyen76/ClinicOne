package com.clinicone.reception;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.OtpPurpose;
import com.clinicone.auth.OtpService;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.auth.RequestOtpResponse;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceptionPatientServiceTest {
    private PatientAccountRepository accountRepository;
    private PatientProfileRepository profileRepository;
    private OtpService otpService;
    private PasswordEncoder passwordEncoder;
    private ReceptionPatientService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        profileRepository = mock(PatientProfileRepository.class);
        otpService = mock(OtpService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new ReceptionPatientService(accountRepository, profileRepository, otpService, passwordEncoder);
    }

    @Test
    void requestsRegistrationOtpOnlyForUnusedPhone() {
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(otpService.requestSmsOtp("0912345678", OtpPurpose.REGISTRATION))
                .thenReturn(new RequestOtpResponse(300, 60));

        RequestOtpResponse response = service.requestOtp(new ReceptionPatientOtpRequest("0912345678"));

        assertThat(response.expiresInSeconds()).isEqualTo(300);
        verify(otpService).requestSmsOtp("0912345678", OtpPurpose.REGISTRATION);
    }

    @Test
    void allowsOtpResendForPendingActivationAccount() {
        PatientAccount pending = new PatientAccount("0912345678", "pending-hash", "Nguyễn An",
                AccountStatus.ACTIVE, true);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(pending));
        when(otpService.requestSmsOtp("0912345678", OtpPurpose.REGISTRATION))
                .thenReturn(new RequestOtpResponse(300, 60));

        service.requestOtp(new ReceptionPatientOtpRequest("0912345678"));

        verify(otpService).requestSmsOtp("0912345678", OtpPurpose.REGISTRATION);
    }

    @Test
    void rejectsOtpRequestForAlreadyActiveAccount() {
        PatientAccount active = new PatientAccount("0912345678", "hash", "Nguyễn An",
                AccountStatus.ACTIVE, false);
        when(accountRepository.findByPhone("0912345678")).thenReturn(Optional.of(active));

        org.junit.jupiter.api.Assertions.assertThrows(com.clinicone.auth.AuthException.class,
                () -> service.requestOtp(new ReceptionPatientOtpRequest("0912345678")));
    }

    @Test
    void createsPendingAccountWithoutIssuingADefaultPasswordAfterOtp() {
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-pending");
        PatientAccount saved = new PatientAccount("0912345678", "encoded-pending", "Nguyễn An",
                AccountStatus.ACTIVE, true);
        when(accountRepository.save(any(PatientAccount.class))).thenReturn(saved);

        ReceptionPatientRegistrationResponse response = service.register(
                new ReceptionPatientRegistrationRequest("0912345678", "123456", "Nguyễn An",
                        LocalDate.of(1995, 6, 7), "Nữ", null, null, "Việt Nam", "Kinh",
                        null, null, null, null, null, null, null));

        assertThat(response.fullName()).isEqualTo("Nguyễn An");
        assertThat(response.mustChangePassword()).isTrue();
        verify(otpService).verifySmsOtp("0912345678", OtpPurpose.REGISTRATION, "123456");
        verify(passwordEncoder).encode(argThat(value -> value != null && !value.equals("123456")));
        verify(profileRepository).save(any());
    }
}

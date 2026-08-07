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
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(otpService.requestSmsOtp("0912345678", OtpPurpose.REGISTRATION))
                .thenReturn(new RequestOtpResponse(300, 60));

        RequestOtpResponse response = service.requestOtp(new ReceptionPatientOtpRequest("0912345678"));

        assertThat(response.expiresInSeconds()).isEqualTo(300);
        verify(otpService).requestSmsOtp("0912345678", OtpPurpose.REGISTRATION);
    }

    @Test
    void createsActiveAccountThatMustChangeTemporaryPasswordAfterOtp() {
        when(accountRepository.existsByPhone("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-temp");
        PatientAccount saved = new PatientAccount("0912345678", "encoded-temp", "Nguyễn An",
                AccountStatus.ACTIVE, true);
        when(accountRepository.save(any(PatientAccount.class))).thenReturn(saved);

        ReceptionPatientRegistrationResponse response = service.register(
                new ReceptionPatientRegistrationRequest("0912345678", "123456", "Nguyễn An",
                        LocalDate.of(1995, 6, 7), "Nữ", null, null, "Việt Nam", "Kinh",
                        null, null, null, null, null, null, null));

        assertThat(response.fullName()).isEqualTo("Nguyễn An");
        assertThat(response.mustChangePassword()).isTrue();
        verify(otpService).verifySmsOtp("0912345678", OtpPurpose.REGISTRATION, "123456");
        verify(passwordEncoder).encode("123456");
        verify(profileRepository).save(any());
    }
}

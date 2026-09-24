package com.clinicone.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

@WebMvcTest(OtpController.class)
@Import({SecurityConfig.class, OtpControllerTest.MockBeans.class})
class OtpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OtpService otpService;

    @Autowired
    private PatientAccountRepository patientAccountRepository;

    @BeforeEach
    void setUp() {
        reset(otpService, patientAccountRepository);
    }

    @Test
    void allowsRegistrationOtpWhenAccountDoesNotExist() throws Exception {
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(otpService.requestSmsOtp("0912345678", OtpPurpose.REGISTRATION))
                .thenReturn(new RequestOtpResponse(300, 60));

        mockMvc.perform(post("/api/v1/auth/request-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"REGISTRATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        verify(otpService).requestSmsOtp("0912345678", OtpPurpose.REGISTRATION);
    }

    @Test
    void rejectsRegistrationOtpWhenAccountAlreadyExists() throws Exception {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A",
                AccountStatus.ACTIVE, false);
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/v1/auth/request-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"REGISTRATION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_USED"));

        verify(otpService, never()).requestSmsOtp(any(), any());
    }

    @Test
    void rejectsRegistrationOtpWhenAccountPendingActivation() throws Exception {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A",
                AccountStatus.ACTIVE, true);
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/v1/auth/request-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"REGISTRATION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_ACTIVATION"));

        verify(otpService, never()).requestSmsOtp(any(), any());
    }

    @Test
    void allowsLoginOtpEvenWhenAccountExists() throws Exception {
        when(otpService.requestSmsOtp("0912345678", OtpPurpose.LOGIN))
                .thenReturn(new RequestOtpResponse(300, 60));

        mockMvc.perform(post("/api/v1/auth/request-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk());

        verify(otpService).requestSmsOtp("0912345678", OtpPurpose.LOGIN);
    }

    @Test
    void rejectsRegistrationVerificationWhenAccountAlreadyExists() throws Exception {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A",
                AccountStatus.ACTIVE, false);
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/v1/auth/verify-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"REGISTRATION\",\"code\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_USED"));

        verify(otpService, never()).verifySmsOtp(any(), any(), any());
    }

    @Test
    void allowsRegistrationVerificationWhenAccountDoesNotExist() throws Exception {
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(otpService.verifySmsOtp("0912345678", OtpPurpose.REGISTRATION, "123456"))
                .thenReturn(new VerifyOtpResponse(true));

        mockMvc.perform(post("/api/v1/auth/verify-sms-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"purpose\":\"REGISTRATION\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        verify(otpService).verifySmsOtp("0912345678", OtpPurpose.REGISTRATION, "123456");
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        OtpService otpService() {
            return mock(OtpService.class);
        }

        @Bean
        PatientAccountRepository patientAccountRepository() {
            return mock(PatientAccountRepository.class);
        }
    }
}

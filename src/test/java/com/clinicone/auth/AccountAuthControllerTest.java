package com.clinicone.auth;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountAuthController.class)
@Import({SecurityConfig.class, AccountAuthControllerTest.MockBeans.class})
class AccountAuthControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountAuthService authService;

    @Autowired
    private com.clinicone.audit.AccessAuditService accessAuditService;

    @Test
    void registersAccountAfterOtpVerification() throws Exception {
        when(authService.register(any())).thenReturn(new RegistrationResponse(
                UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"), "0912345678", "Nguyen Van A"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"fullName\":\"Nguyen Van A\",\"password\":\"password123\",\"dateOfBirth\":\"2005-06-07\",\"gender\":\"Nam\",\"address\":\"Tay Ninh\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("0912345678"));
    }

    @Test
    void logsInAndReturnsBearerToken() throws Exception {
        when(authService.loginBySmsOtp(any())).thenReturn(new LoginResponse(
                "token", "Bearer", Instant.parse("2026-08-04T00:00:00Z"),
                UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"), "Nguyen Van A", false));

        mockMvc.perform(post("/api/v1/auth/login-sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"password\":\"password123\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        verify(accessAuditService).record("PATIENT_LOGIN", "0912345678", "SUCCESS", "/api/v1/auth/login-sms", "127.0.0.1");
    }

    @Test
    void logsInWithPhoneAndPassword() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(
                "token", "Bearer", Instant.parse("2026-08-04T00:00:00Z"),
                UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"), "Nguyen Van A", false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"));
    }

    @Test
    void returnsCurrentPatientProfile() throws Exception {
        when(authService.getProfile(ACCOUNT_ID.toString())).thenReturn(new PatientProfileResponse(
                ACCOUNT_ID, "0912345678", "Nguyen Van A", LocalDate.of(2005, 6, 7), "Nam", "Tay Ninh", AccountStatus.ACTIVE, false));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    void updatesPatientProfile() throws Exception {
        when(authService.updateProfile(org.mockito.ArgumentMatchers.eq(ACCOUNT_ID.toString()), any()))
                .thenReturn(new PatientProfileResponse(ACCOUNT_ID, "0912345678", "Nguyen Thi B", LocalDate.of(2005, 6, 7), "Nam", "Tay Ninh", AccountStatus.ACTIVE, false));

        mockMvc.perform(patch("/api/v1/auth/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nguyen Thi B\",\"dateOfBirth\":\"2005-06-07\",\"gender\":\"Nam\",\"address\":\"Tay Ninh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Thi B"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AccountAuthService accountAuthService() {
            return mock(AccountAuthService.class);
        }

        @Bean
        com.clinicone.audit.AccessAuditService accessAuditService() {
            return mock(com.clinicone.audit.AccessAuditService.class);
        }
    }
}

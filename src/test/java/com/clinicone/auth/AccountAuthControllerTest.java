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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountAuthController.class)
@Import({SecurityConfig.class, AccountAuthControllerTest.MockBeans.class})
class AccountAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountAuthService authService;

    @Test
    void registersAccountAfterOtpVerification() throws Exception {
        when(authService.register(any())).thenReturn(new RegistrationResponse(
                UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"), "user@example.com", "Nguyen Van A"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"phone\":\"0912345678\",\"fullName\":\"Nguyen Van A\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void logsInAndReturnsBearerToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse(
                "token", "Bearer", Instant.parse("2026-08-04T00:00:00Z"),
                UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"), "Nguyen Van A", false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AccountAuthService accountAuthService() {
            return mock(AccountAuthService.class);
        }
    }
}

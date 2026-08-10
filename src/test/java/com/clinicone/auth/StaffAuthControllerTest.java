package com.clinicone.auth;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAuthController.class)
@Import({SecurityConfig.class, StaffAuthControllerTest.MockBeans.class})
class StaffAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAuthService service;

    @Autowired
    private com.clinicone.audit.AccessAuditService accessAuditService;

    @Test
    void staffLoginEndpointIsPublicAndReturnsRole() throws Exception {
        when(service.login(any())).thenReturn(new StaffLoginResponse(
                "staff-token", "Bearer", Instant.parse("2026-08-06T22:00:00Z"),
                UUID.randomUUID(), "Quản trị viên", "ADMIN"));

        mockMvc.perform(post("/api/v1/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("staff-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        verify(accessAuditService).record("STAFF_LOGIN", "admin", "SUCCESS", "/api/v1/staff/auth/login", "127.0.0.1");
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        StaffAuthService staffAuthService() {
            return mock(StaffAuthService.class);
        }

        @Bean
        com.clinicone.audit.AccessAuditService accessAuditService() {
            return mock(com.clinicone.audit.AccessAuditService.class);
        }
    }
}

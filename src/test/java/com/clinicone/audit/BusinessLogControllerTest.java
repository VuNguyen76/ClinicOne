package com.clinicone.audit;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessLogController.class)
@Import({SecurityConfig.class, BusinessLogControllerTest.MockBeans.class})
class BusinessLogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessLogService service;

    @Test
    void coordinatorCanReadAppointmentHistory() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(service.list("APPOINTMENT", appointmentId)).thenReturn(List.of(new BusinessLogResponse(
                UUID.randomUUID(), UUID.randomUUID(), "APPOINTMENT", appointmentId, "BOOKED", "CHECKED_IN",
                "CHECK_IN", "patient", null, Instant.parse("2026-08-10T01:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/audit/appointments/{id}", appointmentId)
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previousStatus").value("BOOKED"))
                .andExpect(jsonPath("$[0].nextStatus").value("CHECKED_IN"));
    }

    @Test
    void doctorCannotReadBusinessHistory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit/appointments/{id}", UUID.randomUUID())
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanReadPagedBusinessHistory() throws Exception {
        UUID entityId = UUID.randomUUID();
        when(service.page("APPOINTMENT", entityId, 0, 50))
                .thenReturn(new BusinessLogPageResponse(List.of(), 0, 50, 0, 0, true));

        mockMvc.perform(get("/api/v1/admin/audit/search")
                        .param("entityType", "APPOINTMENT")
                        .param("entityId", entityId.toString())
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.items").isArray());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        BusinessLogService businessLogService() {
            return mock(BusinessLogService.class);
        }

        @Bean
        BusinessLogIntegrityJob businessLogIntegrityJob() {
            return mock(BusinessLogIntegrityJob.class);
        }
    }
}

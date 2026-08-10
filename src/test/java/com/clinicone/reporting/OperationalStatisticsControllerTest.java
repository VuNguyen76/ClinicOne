package com.clinicone.reporting;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalStatisticsController.class)
@Import({SecurityConfig.class, OperationalStatisticsControllerTest.MockBeans.class})
class OperationalStatisticsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationalStatisticsService service;

    @Test
    void coordinatorCanReadStatistics() throws Exception {
        when(service.summarize(any(), any(), any(), any())).thenReturn(new OperationalStatisticsResponse(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), "Khám Tổng Quát", null,
                4, 3, 1, 0, 2, 1, null, null));

        mockMvc.perform(get("/api/v1/admin/statistics")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-10")
                        .param("specialty", "Khám Tổng Quát")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAppointments").value(4))
                .andExpect(jsonPath("$.checkedInAppointments").value(3));
    }

    @Test
    void doctorCannotReadAdminStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/statistics")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-10")
                        .param("specialty", "Khám Tổng Quát")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        OperationalStatisticsService operationalStatisticsService() {
            return mock(OperationalStatisticsService.class);
        }
    }
}

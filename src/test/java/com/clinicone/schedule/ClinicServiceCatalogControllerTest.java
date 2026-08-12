package com.clinicone.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClinicServiceCatalogController.class)
@Import({com.clinicone.config.SecurityConfig.class, ClinicServiceCatalogControllerTest.MockBeans.class})
class ClinicServiceCatalogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicServiceManagementService serviceManagement;

    @Test
    void patientCanReadActiveServicesForBooking() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceManagement.listActive()).thenReturn(List.of(new ClinicServiceResponse(
                id, "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường", 30, true, List.of())));

        mockMvc.perform(get("/api/v1/services")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                "patient-id", null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Khám tổng quát cơ bản"))
                .andExpect(jsonPath("$[0].durationMinutes").value(30));
    }

    @Test
    void anonymousCannotReadServices() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ClinicServiceManagementService clinicServiceManagementService() {
            return mock(ClinicServiceManagementService.class);
        }
    }
}

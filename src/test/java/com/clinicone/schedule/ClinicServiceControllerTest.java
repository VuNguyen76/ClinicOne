package com.clinicone.schedule;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClinicServiceController.class)
@org.springframework.context.annotation.Import({SecurityConfig.class, ClinicServiceControllerTest.MockBeans.class})
class ClinicServiceControllerTest {
    private static final UUID SERVICE_ID = UUID.fromString("8d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired MockMvc mockMvc;
    @Autowired ClinicServiceManagementService service;

    @Test
    void coordinatorCanCreateClinicService() throws Exception {
        when(service.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/admin/services")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Khám tổng quát cơ bản\",\"specialty\":\"Khám Tổng Quát\",\"visitType\":\"Khám thường\",\"durationMinutes\":30,\"doctorIds\":[\"9d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Khám tổng quát cơ bản"))
                .andExpect(jsonPath("$.durationMinutes").value(30));
    }

    @Test
    void adminCanChangeClinicServiceCatalog() throws Exception {
        mockMvc.perform(post("/api/v1/admin/services")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Khám tổng quát cơ bản\",\"specialty\":\"Khám Tổng Quát\",\"visitType\":\"Khám thường\",\"durationMinutes\":30,\"doctorIds\":[\"9d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidDurationIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/services")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Khám tổng quát cơ bản\",\"specialty\":\"Khám Tổng Quát\",\"visitType\":\"Khám thường\",\"durationMinutes\":121,\"doctorIds\":[\"9d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCanReadClinicServiceCatalog() throws Exception {
        when(service.list()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/admin/services")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Khám tổng quát cơ bản"));
    }

    private static ClinicServiceResponse response() {
        return new ClinicServiceResponse(SERVICE_ID, "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường",
                30, true, List.of(new EligibleDoctorResponse(UUID.randomUUID(), UUID.randomUUID(), "Bác sĩ Nguyễn An")));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ClinicServiceManagementService clinicServiceManagementService() {
            return mock(ClinicServiceManagementService.class);
        }
    }
}

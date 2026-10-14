package com.clinicone.reason;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReasonCatalogAdminController.class)
@Import({SecurityConfig.class, ReasonCatalogAdminControllerTest.MockBeans.class})
class ReasonCatalogAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReasonCatalogService service;

    @Test
    void coordinatorCanReadCancellationReasonsButCannotManageThem() throws Exception {
        when(service.list(ReasonCatalogType.APPOINTMENT_CANCELLATION, false)).thenReturn(List.of(
                new ReasonCatalogResponse(UUID.randomUUID(), ReasonCatalogType.APPOINTMENT_CANCELLATION,
                        "SCHEDULE_CHANGE", "Thay đổi kế hoạch", true)));

        mockMvc.perform(get("/api/v1/admin/reason-catalog")
                        .param("type", "APPOINTMENT_CANCELLATION")
                        .param("activeOnly", "false")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Thay đổi kế hoạch"));

        mockMvc.perform(post("/api/v1/admin/reason-catalog")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPOINTMENT_CANCELLATION\",\"code\":\"TRAVEL\",\"label\":\"Đi công tác\"}"))
                .andExpect(status().isForbidden());

        verify(service).list(ReasonCatalogType.APPOINTMENT_CANCELLATION, false);
    }

    @Test
    void doctorCannotReadAdminCancellationReasons() throws Exception {
        clearInvocations(service);
        mockMvc.perform(get("/api/v1/admin/reason-catalog")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void administratorCanManageCancellationReasons() throws Exception {
        when(service.create(any())).thenReturn(new ReasonCatalogResponse(UUID.randomUUID(),
                ReasonCatalogType.APPOINTMENT_CANCELLATION, "TRAVEL", "Đi công tác", true));

        mockMvc.perform(post("/api/v1/admin/reason-catalog")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPOINTMENT_CANCELLATION\",\"code\":\"TRAVEL\",\"label\":\"Đi công tác\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRAVEL"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ReasonCatalogService reasonCatalogService() {
            return mock(ReasonCatalogService.class);
        }
    }
}

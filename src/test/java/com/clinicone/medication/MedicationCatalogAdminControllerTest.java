package com.clinicone.medication;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicationCatalogAdminController.class)
@Import({SecurityConfig.class, MedicationCatalogAdminControllerTest.MockBeans.class})
class MedicationCatalogAdminControllerTest {
    private static final UUID MEDICATION_ID = UUID.fromString("3c9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicationCatalogService service;

    @Test
    void adminCanListAndCreateMedication() throws Exception {
        when(service.list(false)).thenReturn(List.of(response(true)));
        when(service.create(any())).thenReturn(response(true));

        mockMvc.perform(get("/api/v1/admin/medications").with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PCM500"));

        mockMvc.perform(post("/api/v1/admin/medications")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PCM500\",\"name\":\"Paracetamol 500 mg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void nonAdminCannotChangeMedicationCatalog() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/medications/" + MEDICATION_ID + "/active")
                        .param("value", "false")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSuspendInsteadOfDeletingMedication() throws Exception {
        when(service.setActive(eq(MEDICATION_ID), eq(false))).thenReturn(response(false));

        mockMvc.perform(patch("/api/v1/admin/medications/" + MEDICATION_ID + "/active")
                        .param("value", "false")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("admin-1", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static MedicationResponse response(boolean active) {
        return new MedicationResponse(MEDICATION_ID, "PCM500", "Paracetamol 500 mg", active);
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        MedicationCatalogService medicationCatalogService() {
            return mock(MedicationCatalogService.class);
        }
    }
}

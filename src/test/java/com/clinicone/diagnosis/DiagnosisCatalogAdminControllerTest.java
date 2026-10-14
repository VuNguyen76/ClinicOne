package com.clinicone.diagnosis;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiagnosisCatalogAdminController.class)
@Import({SecurityConfig.class, DiagnosisCatalogAdminControllerTest.MockBeans.class})
class DiagnosisCatalogAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiagnosisCatalogService service;

    @Test
    void administratorCanViewDiagnosesButCannotCreateThem() throws Exception {
        when(service.list(false)).thenReturn(List.of(
                new DiagnosisCatalogResponse(UUID.randomUUID(), "I10", "Tăng huyết áp", true)));

        mockMvc.perform(get("/api/v1/admin/diagnoses")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("I10"));

        mockMvc.perform(post("/api/v1/admin/diagnoses")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"I10\",\"name\":\"Tăng huyết áp\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanCreateDiagnoses() throws Exception {
        when(service.create(any())).thenReturn(new DiagnosisCatalogResponse(UUID.randomUUID(), "I10", "Tăng huyết áp", true));

        mockMvc.perform(post("/api/v1/admin/diagnoses")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"I10\",\"name\":\"Tăng huyết áp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("I10"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        DiagnosisCatalogService diagnosisCatalogService() {
            return mock(DiagnosisCatalogService.class);
        }
    }
}

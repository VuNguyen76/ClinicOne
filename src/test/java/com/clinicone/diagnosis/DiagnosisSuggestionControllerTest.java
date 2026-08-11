package com.clinicone.diagnosis;

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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiagnosisSuggestionController.class)
@Import({SecurityConfig.class, DiagnosisSuggestionControllerTest.MockBeans.class})
class DiagnosisSuggestionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiagnosisCatalogService service;

    @Test
    void doctorCanSearchActiveDiagnosisSuggestions() throws Exception {
        when(service.suggestions("đau")).thenReturn(List.of(
                new DiagnosisCatalogResponse(UUID.randomUUID(), "HEADACHE_TENSION", "Đau đầu căng thẳng", true)));

        mockMvc.perform(get("/api/v1/doctor/diagnoses/suggestions").param("query", "đau")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("HEADACHE_TENSION"));
    }

    @Test
    void nonDoctorCannotSearchDiagnosisSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/diagnoses/suggestions").param("query", "đau")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-1", null,
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

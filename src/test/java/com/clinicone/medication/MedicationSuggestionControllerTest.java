package com.clinicone.medication;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicationSuggestionController.class)
@Import({SecurityConfig.class, MedicationSuggestionControllerTest.MockBeans.class})
class MedicationSuggestionControllerTest {
    private static final UUID MEDICATION_ID = UUID.fromString("3c9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicationCatalogService service;

    @Test
    void doctorCanListActiveMedications() throws Exception {
        when(service.list(true)).thenReturn(List.of(
                new MedicationResponse(MEDICATION_ID, "MED-PARA-500", "Paracetamol 500mg", true)
        ));

        mockMvc.perform(get("/api/v1/doctor/medications").with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MED-PARA-500"))
                .andExpect(jsonPath("$[0].name").value("Paracetamol 500mg"));
    }

    @Test
    void doctorCanSearchMedicationSuggestions() throws Exception {
        when(service.suggestions("para")).thenReturn(List.of(
                new MedicationResponse(MEDICATION_ID, "MED-PARA-500", "Paracetamol 500mg", true)
        ));

        mockMvc.perform(get("/api/v1/doctor/medications/suggestions")
                        .param("query", "para")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MED-PARA-500"));
    }

    @Test
    void nonDoctorCannotAccessDoctorMedications() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/medications").with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return new UsernamePasswordAuthenticationToken("user", "n/a", List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        MedicationCatalogService medicationCatalogService() {
            return mock(MedicationCatalogService.class);
        }
    }
}

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReasonCatalogController.class)
@Import({SecurityConfig.class, ReasonCatalogControllerTest.MockBeans.class})
class ReasonCatalogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicalReasonRepository clinicalReasonRepository;

    @Test
    void administratorCannotCreateClinicalReasons() throws Exception {
        clearInvocations(clinicalReasonRepository);
        mockMvc.perform(post("/api/v1/reasons/clinical")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialtyCode\":\"CARD\",\"specialtyName\":\"Tim mạch\",\"symptomName\":\"Đau ngực\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clinicalReasonRepository);
    }

    @Test
    void coordinatorCanCreateClinicalReasons() throws Exception {
        when(clinicalReasonRepository.existsBySpecialtyNameIgnoreCaseAndSymptomNameIgnoreCase("Tim mạch", "Đau ngực"))
                .thenReturn(false);
        when(clinicalReasonRepository.save(any(ClinicalReason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/reasons/clinical")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialtyCode\":\"CARD\",\"specialtyName\":\"Tim mạch\",\"symptomName\":\"Đau ngực\",\"sortOrder\":1}"))
                .andExpect(status().isCreated());
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

        @Bean
        ClinicalReasonRepository clinicalReasonRepository() {
            return mock(ClinicalReasonRepository.class);
        }
    }
}

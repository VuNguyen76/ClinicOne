package com.clinicone.examination;

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExaminationSessionController.class)
@Import({SecurityConfig.class, ExaminationSessionControllerTest.MockBeans.class})
class ExaminationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExaminationSessionService service;

    @Test
    void patientCanReadOwnExaminationSessions() throws Exception {
        when(service.list("patient-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/examinations")
                        .with(authentication(authenticated("patient-1", "ROLE_PATIENT"))))
                .andExpect(status().isOk());
    }

    @Test
    void staffCannotReadPatientExaminationSessionsThroughPatientEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/examinations")
                        .with(authentication(authenticated("staff-1", "ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String principal, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ExaminationSessionService examinationSessionService() {
            return mock(ExaminationSessionService.class);
        }
    }
}

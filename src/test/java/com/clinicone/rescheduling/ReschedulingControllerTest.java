package com.clinicone.rescheduling;

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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReschedulingController.class)
@Import({SecurityConfig.class, ReschedulingControllerTest.MockBeans.class})
class ReschedulingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReschedulingService service;

    @Test
    void adminCannotResolveReschedulingCase() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rescheduling/{id}/resolve", UUID.randomUUID())
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanResolveReschedulingCase() throws Exception {
        when(service.resolve(any(), any(), any())).thenReturn(mock(RescheduleCaseResponse.class));

        mockMvc.perform(post("/api/v1/admin/rescheduling/{id}/resolve", UUID.randomUUID())
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    private static String requestBody() {
        return "{\"appointmentDate\":\"" + LocalDate.now().plusDays(1)
                + "\",\"startTime\":\"09:00\",\"doctorName\":\"Bác sĩ kiểm tra\"}";
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ReschedulingService reschedulingService() {
            return mock(ReschedulingService.class);
        }
    }
}

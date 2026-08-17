package com.clinicone.schedule;

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
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentHoldController.class)
@Import({SecurityConfig.class, AppointmentHoldControllerTest.MockBeans.class})
class AppointmentHoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentHoldService appointmentHoldService;

    @Test
    void staffCannotCreatePatientAppointmentHold() throws Exception {
        mockMvc.perform(post("/api/v1/appointment-holds")
                        .with(authentication(authenticated("staff-1", "ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"specialty\":\"Nội tổng quát\",\"doctorName\":\"Bác sĩ An\","
                                + "\"appointmentDate\":\"2099-01-01\",\"startTime\":\"08:30\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedLoginSessionWinsOverClientSuppliedHoldSession() throws Exception {
        mockMvc.perform(post("/api/v1/appointment-holds")
                        .with(authentication(authenticated("patient-1", "ROLE_PATIENT")))
                        .requestAttr("clinicOneLoginSessionId", "server-session")
                        .header("X-ClinicOne-Session", "client-session")
                        .contentType("application/json")
                        .content("{\"specialty\":\"Nội tổng quát\",\"doctorName\":\"Bác sĩ An\","
                                + "\"appointmentDate\":\"2099-01-01\",\"startTime\":\"08:30\"}"))
                .andExpect(status().isCreated());
        verify(appointmentHoldService).create(eq("patient-1"), org.mockito.ArgumentMatchers.any(), eq("server-session"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String principal, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AppointmentHoldService appointmentHoldService() {
            return mock(AppointmentHoldService.class);
        }
    }
}

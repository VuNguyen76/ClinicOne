package com.clinicone.appointment;

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorAppointmentsController.class)
@Import({SecurityConfig.class, DoctorAppointmentsControllerTest.MockBeans.class})
class DoctorAppointmentsControllerTest {
    private static final UUID DOCTOR_ID = UUID.fromString("8131b3ba-b830-4616-80cf-07a04e49ace2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorAppointmentsService service;

    @Test
    void listsAppointmentsOnlyForTheAuthenticatedDoctorAndRequestedDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/appointments")
                        .queryParam("from", "2026-10-13")
                        .queryParam("to", "2026-11-12")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                DOCTOR_ID.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))))))
                .andExpect(status().isOk());

        verify(service).list(DOCTOR_ID, LocalDate.parse("2026-10-13"), LocalDate.parse("2026-11-12"));
    }

    @Test
    void deniesAppointmentListToNonDoctors() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/appointments")
                        .queryParam("from", "2026-10-13")
                        .queryParam("to", "2026-11-12")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                DOCTOR_ID.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        DoctorAppointmentsService doctorAppointmentsService() {
            return mock(DoctorAppointmentsService.class);
        }
    }
}

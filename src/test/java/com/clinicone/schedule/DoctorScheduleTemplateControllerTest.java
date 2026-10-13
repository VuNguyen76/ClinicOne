package com.clinicone.schedule;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorScheduleTemplateController.class)
@Import({SecurityConfig.class, DoctorScheduleTemplateControllerTest.MockBeans.class})
class DoctorScheduleTemplateControllerTest {
    private static final UUID DOCTOR_ID = UUID.fromString("8131b3ba-b830-4616-80cf-07a04e49ace2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduleTemplateService scheduleTemplateService;

    @Test
    void returnsSchedulesScopedToTheAuthenticatedDoctor() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/schedule-templates")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                DOCTOR_ID.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))))))
                .andExpect(status().isOk());

        verify(scheduleTemplateService).listForDoctor(DOCTOR_ID);
    }

    @Test
    void deniesScheduleEndpointToNonDoctors() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/schedule-templates")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                DOCTOR_ID.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ScheduleTemplateService scheduleTemplateService() {
            return mock(ScheduleTemplateService.class);
        }
    }
}

package com.clinicone.notification;

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
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientNotificationController.class)
@Import({SecurityConfig.class, PatientNotificationControllerTest.MockBeans.class})
class PatientNotificationControllerTest {
    private static final UUID PATIENT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID NOTIFICATION_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientNotificationService service;

    @Test
    void patientCanListNotificationsAndUnreadCount() throws Exception {
        when(service.list(PATIENT_ID.toString())).thenReturn(List.of(response()));
        when(service.unreadCount(PATIENT_ID.toString())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Phiếu khám đã có kết quả"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void patientCanMarkOwnNotificationRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/" + NOTIFICATION_ID + "/read")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isNoContent());
        verify(service).markRead(PATIENT_ID.toString(), NOTIFICATION_ID.toString());
    }

    @Test
    void patientCanMarkAllNotificationsRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isNoContent());
        verify(service).markAllRead(PATIENT_ID.toString());
    }

    @Test
    void staffCannotReadPatientNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(PATIENT_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static PatientNotificationResponse response() {
        return new PatientNotificationResponse(NOTIFICATION_ID, "MEDICAL_RECORD_SIGNED",
                "Phiếu khám đã có kết quả", "Bác sĩ đã ký phiếu khám.", "/medical-records/" + NOTIFICATION_ID,
                false, Instant.parse("2026-08-07T09:30:00Z"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        PatientNotificationService patientNotificationService() {
            return mock(PatientNotificationService.class);
        }
    }
}

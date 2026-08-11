package com.clinicone.appointment;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import({SecurityConfig.class, AppointmentControllerTest.MockBeans.class})
class AppointmentControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void listsAppointmentsForAuthenticatedPatient() throws Exception {
        when(appointmentService.list(ACCOUNT_ID.toString())).thenReturn(List.of(new AppointmentResponse(
                UUID.randomUUID(), "CL-20260810-1234", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu", "BOOKED", "Đã đặt")));

        mockMvc.perform(get("/api/v1/appointments")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appointmentCode").value("CL-20260810-1234"))
                .andExpect(jsonPath("$[0].statusLabel").value("Đã đặt"));
    }

    @Test
    void createsAppointmentForAuthenticatedPatient() throws Exception {
        when(appointmentService.create(eq(ACCOUNT_ID.toString()), any())).thenReturn(new AppointmentResponse(
                UUID.randomUUID(), "CL-20260810-1234", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu", "BOOKED", "Đã đặt"));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialty\":\"Nội khoa\",\"doctorName\":\"BS. Nguyễn An\",\"appointmentDate\":\"2099-01-01\",\"startTime\":\"08:30\",\"reason\":\"Đau đầu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentCode").value("CL-20260810-1234"));
    }

    @Test
    void returnsAppointmentDetail() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.get(eq(ACCOUNT_ID.toString()), eq(appointmentId.toString()))).thenReturn(new AppointmentResponse(
                appointmentId, "CL-20260810-1234", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu", "BOOKED", "Đã đặt"));

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentCode").value("CL-20260810-1234"));
    }

    @Test
    void cancelsAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bận việc\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reschedulesAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.reschedule(eq(ACCOUNT_ID.toString()), eq(appointmentId.toString()), any())).thenReturn(new AppointmentResponse(
                appointmentId, "CL-20260810-1234", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), "Đau đầu", "BOOKED", "Đã đặt"));

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/reschedule")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                ACCOUNT_ID.toString(), null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentDate\":\"2026-08-11\",\"startTime\":\"10:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentDate").value("2026-08-11"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AppointmentService appointmentService() {
            return mock(AppointmentService.class);
        }
    }
}

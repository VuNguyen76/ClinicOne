package com.clinicone.reception;

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

@WebMvcTest(ReceptionController.class)
@Import({SecurityConfig.class, ReceptionControllerTest.MockBeans.class})
class ReceptionControllerTest {
    private static final UUID APPOINTMENT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID STAFF_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReceptionService service;

    @Autowired
    private ReceptionPatientService patientService;

    @Test
    void receptionistCanSearchTodaysAppointmentByPhone() throws Exception {
        when(service.search(eq("0912345678"), eq(LocalDate.of(2026, 8, 7))))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/reception/appointments?query=0912345678&date=2026-08-07")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientName").value("Nguyễn Thanh Vũ"))
                .andExpect(jsonPath("$[0].roomCode").value("NOI-01"));
    }

    @Test
    void doctorCannotUseReceptionSearch() throws Exception {
        mockMvc.perform(get("/api/v1/reception/appointments?query=0912345678")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCanConfirmArrival() throws Exception {
        when(service.checkIn(eq(APPOINTMENT_ID), any())).thenReturn(responseWithTicket());

        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/check-in")
                .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"roomCode\":\"NOI-01\",\"reason\":\"QR phòng bị lỗi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"));
    }

    @Test
    void receptionistCanRecordPatientLeftBeforeExam() throws Exception {
        when(service.leaveBeforeExam(eq(APPOINTMENT_ID), eq("Người bệnh bận việc đột xuất")))
                .thenReturn(responseWithLeftTicket());

        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/leave")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Người bệnh bận việc đột xuất\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.queueStatus").value("LEFT_BEFORE_EXAM"));
    }

    @Test
    void receptionistCanSeeWhetherTheAccountStillNeedsPasswordChange() throws Exception {
        when(service.profiles("0912345678")).thenReturn(List.of(new ReceptionPatientProfileResponse(
                UUID.randomUUID(), "Nguyễn Thanh Vũ", "Bản thân", LocalDate.of(2005, 6, 7), true,
                com.clinicone.auth.AccountStatus.ACTIVE, true)));

        mockMvc.perform(get("/api/v1/reception/profiles?phone=0912345678")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mustChangePassword").value(true))
                .andExpect(jsonPath("$[0].accountStatus").value("ACTIVE"));
    }

    @Test
    void receptionistCannotUseExceptionCheckInWithoutReason() throws Exception {
        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/check-in")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"roomCode\":\"NOI-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receptionistCanCreateWalkInFromExistingPatientAccount() throws Exception {
        when(service.createWalkIn(any())).thenReturn(responseWithTicket());
        String today = LocalDate.now().toString();

        mockMvc.perform(post("/api/v1/reception/walk-in")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"phone\":\"0912345678\",\"doctorId\":\"7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13\","
                                + "\"appointmentDate\":\"" + today + "\",\"startTime\":\"09:00\","
                                + "\"reason\":\"Đau đầu từ sáng\",\"exceptionReason\":\"Người bệnh đến quầy không có lịch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.appointmentCode").value("CL-20260807-1234"));
    }

    @Test
    void receptionistCannotCreateWalkInWithInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/v1/reception/walk-in")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"phone\":\"09123\",\"doctorId\":\"7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13\","
                                + "\"appointmentDate\":\"2026-08-07\",\"startTime\":\"09:00\","
                                + "\"reason\":\"Đau đầu từ sáng\",\"exceptionReason\":\"Người bệnh đến quầy không có lịch\"}"))
                .andExpect(status().isBadRequest());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(STAFF_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static ReceptionAppointmentResponse response() {
        return new ReceptionAppointmentResponse(APPOINTMENT_ID, "CL-20260807-1234", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), "Nội tổng quát", "BS. Nguyễn An", "NOI-01", "Phòng Nội 01", null,
                "Nguyễn Thanh Vũ", "0912345678", "BOOKED", null, null, null);
    }

    private static ReceptionAppointmentResponse responseWithTicket() {
        return new ReceptionAppointmentResponse(APPOINTMENT_ID, "CL-20260807-1234", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), "Nội tổng quát", "BS. Nguyễn An", "NOI-01", "Phòng Nội 01", null,
                "Nguyễn Thanh Vũ", "0912345678", "BOOKED", 5, "WAITING", "Đang chờ");
    }

    private static ReceptionAppointmentResponse responseWithLeftTicket() {
        return new ReceptionAppointmentResponse(APPOINTMENT_ID, "CL-20260807-1234", LocalDate.of(2026, 8, 7),
                LocalTime.of(9, 0), "Nội tổng quát", "BS. Nguyễn An", "NOI-01", "Phòng Nội 01", null,
                "Nguyễn Thanh Vũ", "0912345678", "NOT_PERFORMED", 5, "LEFT_BEFORE_EXAM", "Rời trước khám");
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ReceptionService receptionService() {
            return mock(ReceptionService.class);
        }

        @Bean
        ReceptionPatientService receptionPatientService() {
            return mock(ReceptionPatientService.class);
        }
    }
}

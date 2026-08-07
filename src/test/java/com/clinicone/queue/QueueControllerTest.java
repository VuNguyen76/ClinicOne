package com.clinicone.queue;

import com.clinicone.config.SecurityConfig;
import com.clinicone.auth.StaffRole;
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

@WebMvcTest(QueueController.class)
@Import({SecurityConfig.class, QueueControllerTest.MockBeans.class})
class QueueControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID APPOINTMENT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID TICKET_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueService queueService;

    @Test
    void checksInAppointmentFromRoomQr() throws Exception {
        when(queueService.checkIn(eq(ACCOUNT_ID.toString()), eq("NOI-01"), eq(APPOINTMENT_ID)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/rooms/NOI-01/queue/check-in")
                        .with(authentication(authenticated("ROLE_PATIENT")))
                        .contentType("application/json")
                        .content("{\"appointmentId\":\"" + APPOINTMENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.roomCode").value("NOI-01"));
    }

    @Test
    void listsRoomQueueForStaffScreen() throws Exception {
        when(queueService.listForStaff(eq("NOI-01"), eq(LocalDate.of(2026, 8, 6)), eq(ACCOUNT_ID.toString()), eq(StaffRole.COORDINATOR)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/rooms/NOI-01/queue?date=2026-08-06")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueNumber").value(5));
    }

    @Test
    void doctorGetsOnlyOwnWorkspaceQueue() throws Exception {
        when(queueService.doctorQueue(eq(LocalDate.of(2026, 8, 6)), eq(ACCOUNT_ID.toString())))
                .thenReturn(new DoctorQueueResponse("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát", List.of(response())));

        mockMvc.perform(get("/api/v1/doctor/queue?date=2026-08-06")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value("NOI-01"))
                .andExpect(jsonPath("$.tickets[0].queueNumber").value(5));
    }

    @Test
    void callsTicket() throws Exception {
        when(queueService.call(TICKET_ID)).thenReturn(response());

        mockMvc.perform(post("/api/v1/queue/" + TICKET_ID + "/call")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));
    }

    @Test
    void doctorCanCallOwnTicket() throws Exception {
        when(queueService.call(TICKET_ID, ACCOUNT_ID.toString())).thenReturn(response());

        mockMvc.perform(post("/api/v1/queue/" + TICKET_ID + "/call")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));
    }

    @Test
    void doctorCanCallNextPatientFromOwnQueue() throws Exception {
        when(queueService.callNext(eq(ACCOUNT_ID.toString()), eq(LocalDate.of(2026, 8, 6))))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/doctor/queue/call-next?date=2026-08-06")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));
    }

    @Test
    void doctorMustSignMedicalRecordBeforeCompleting() throws Exception {
        mockMvc.perform(post("/api/v1/queue/" + TICKET_ID + "/complete")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isConflict());
    }

    @Test
    void receptionistCanCloseQueueTicketWhenPatientLeavesBeforeExam() throws Exception {
        when(queueService.leaveBeforeExam(eq(TICKET_ID), eq("Bệnh nhân bận việc")))
                .thenReturn(new QueueTicketResponse(TICKET_ID, 5, "NOI-01", "Phòng Nội tổng quát 01",
                        LocalDate.of(2026, 8, 6), LocalTime.of(9, 0), "LEFT_BEFORE_EXAM", "Rời trước khám",
                        "CL-20260806-1234", "Nội tổng quát", "BS. Nguyễn An"));

        mockMvc.perform(post("/api/v1/queue/" + TICKET_ID + "/leave")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Bệnh nhân bận việc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT_BEFORE_EXAM"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(ACCOUNT_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static QueueTicketResponse response() {
        return new QueueTicketResponse(TICKET_ID, 5, "NOI-01", "Phòng Nội tổng quát 01",
                LocalDate.of(2026, 8, 6), LocalTime.of(9, 0), "CALLED", "Đang được gọi",
                "CL-20260806-1234", "Nội tổng quát", "BS. Nguyễn An");
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        QueueService queueService() {
            return mock(QueueService.class);
        }
    }
}

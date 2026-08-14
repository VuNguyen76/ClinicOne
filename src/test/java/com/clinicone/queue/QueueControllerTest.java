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
@Import({ SecurityConfig.class, QueueControllerTest.MockBeans.class })
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
    }// luồng chính REC 01 checkin mã QR

    @Test
    void receptionistCanCheckInForPatient() throws Exception {
        when(queueService.checkIn(eq(ACCOUNT_ID.toString()), eq("NOI-01"), eq(APPOINTMENT_ID)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/rooms/NOI-01/queue/check-in")
                .with(authentication(authenticated("ROLE_RECEPTIONIST"))) // Quyền Lễ tân xử lý luồng phụ
                .contentType("application/json")
                .content("{\"appointmentId\":\"" + APPOINTMENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.roomCode").value("NOI-01"));
    }// luồng phụ REC 1 check in bằng mã lịch hẹn

    @Test
    void listsRoomQueueForStaffScreen() throws Exception {
        when(queueService.listForStaff(eq("NOI-01"), eq(LocalDate.of(2026, 8, 6)), eq(ACCOUNT_ID.toString()),
                eq(StaffRole.COORDINATOR)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/rooms/NOI-01/queue?date=2026-08-06")
                .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueNumber").value(5));
    }

    @Test
    void doctorGetsOnlyOwnWorkspaceQueue() throws Exception {
        when(queueService.doctorQueue(eq(LocalDate.of(2026, 8, 6)), eq(ACCOUNT_ID.toString())))
                .thenReturn(new DoctorQueueResponse("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát",
                        List.of(response())));

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
    void receptionistCanProcessWalkInCheckIn() throws Exception {
        // AC-REC-03-01: Lễ tân có thể check-in cho bệnh nhân không hẹn trước (Walk-in)                    
        WalkInCheckInRequest request = new WalkInCheckInRequest(
                "0912345678", "Nội tổng quát", "Khám tổng quát định kỳ", null, null
        );

        // Giả lập Service xử lý thành công và trả về vé (ticket)
        when(queueService.processWalkInCheckIn(any(WalkInCheckInRequest.class), eq(ACCOUNT_ID.toString())))
                .thenReturn(response()); // Biến response() đã có sẵn trong file test của bạn

        String requestJson = "{\"phone\":\"0912345678\",\"specialty\":\"Nội tổng quát\",\"reason\":\"Khám tổng quát định kỳ\"}";

        mockMvc.perform(post("/api/v1/queue/walk-in")
                .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                .contentType("application/json")
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").exists());
    }

    @Test
    void walkInRequestFailsWhenMissingRequiredFields() throws Exception {
        // AC-REC-03-01: Kiểm tra DTO chặn các request không hợp lệ (thiếu lý do)
        // Nếu Frontend/Hacker cố tình truyền thiếu trường bắt buộc, API phải trả về 400 Bad Request
        String invalidRequestJson = "{"
                + "\"phone\":\"0912345678\","
                + "\"specialty\":\"Nội tổng quát\""
                + "}"; // Bỏ trống 'reason' cố ý

        mockMvc.perform(post("/api/v1/queue/walk-in")
                .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                .contentType("application/json")
                .content(invalidRequestJson))
                .andExpect(status().isBadRequest()); // Khẳng định DTO Validation hoạt động
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

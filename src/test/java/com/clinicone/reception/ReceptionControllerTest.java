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

    // Nhóm đối chiếu thông tin
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
    void receptionistCanSearchTodaysAppointmentByCode() throws Exception {
        // 1. Giả lập Service trả về danh sách lịch hẹn khi tìm theo Mã lịch hẹn
        when(service.search(eq("CL-20260807-1234"), eq(LocalDate.of(2026, 8, 7))))
                .thenReturn(List.of(response()));

        // 2. Gửi request GET kèm query là mã lịch hẹn với quyền Lễ tân
        mockMvc.perform(get("/api/v1/reception/appointments?query=CL-20260807-1234&date=2026-08-07")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                // 3. Kiểm tra kết quả trả về mã 200 OK và đúng thông tin
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appointmentCode").value("CL-20260807-1234"))
                .andExpect(jsonPath("$[0].patientName").value("Nguyễn Thanh Vũ"))
                .andExpect(jsonPath("$[0].roomCode").value("NOI-01"));
    }

    @Test
    void receptionistGetsEmptyListWhenAppointmentNotFound() throws Exception {
        when(service.search(eq("0999999999"), eq(LocalDate.of(2026, 8, 7))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reception/appointments?query=0999999999&date=2026-08-07")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }// Mã lịch hẹn không tồn tại

    @Test
    void receptionistCanSearchMultipleAppointmentsForSamePhone() throws Exception {
        ReceptionAppointmentResponse secondAppointment = new ReceptionAppointmentResponse(
                UUID.randomUUID(), "CL-20260807-9999", LocalDate.of(2026, 8, 7),
                LocalTime.of(10, 30), "Răng Hàm Mặt", "BS. Tran B", "RHM-01", "Phòng RHM 01", null,
                "Nguyễn Thanh Vũ", "0912345678", "BOOKED", null, null, null);

        when(service.search(eq("0912345678"), eq(LocalDate.of(2026, 8, 7))))
                .thenReturn(List.of(response(), secondAppointment));

        mockMvc.perform(get("/api/v1/reception/appointments?query=0912345678&date=2026-08-07")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].appointmentCode").value("CL-20260807-1234"))
                .andExpect(jsonPath("$[1].appointmentCode").value("CL-20260807-9999"));
    }// Nhập SDT trả đủ số lịch hẹn >1

    @Test
    void receptionistGetsEmptyListWhenProfileNotFound() throws Exception {
        when(service.profiles("0900000000")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reception/profiles?phone=0900000000")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }// Tra cứu hồ sơ bệnh nhân không tồn tại

    // Nhóm Bảo mật API cho nhóm tiếp nhận
    @Test
    void doctorCannotUseReceptionSearch() throws Exception {
        mockMvc.perform(get("/api/v1/reception/appointments?query=0912345678")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotPerformReceptionCheckIn() throws Exception {
        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/check-in")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"roomCode\":\"NOI-01\",\"reason\":\"QR phòng bị lỗi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotSearchAppointments() throws Exception {
        mockMvc.perform(get("/api/v1/reception/appointments?query=0912345678&date=2026-08-07"))
                .andExpect(status().isForbidden());
    }// chặn người dùng tra MLH khi chưa đăng nhập 403

    @Test
    void doctorCannotSearchReceptionPatientProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/reception/profiles?phone=0912345678")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }// Chặn Bác sĩ tra cứu danh sách hồ sơ

    @Test
    void patientCannotSearchReceptionPatientProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/reception/profiles?phone=0912345678")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isForbidden());
    }// Chặn bệnh nhân tra cứu danh sách hồ sơ

    @Test
    void adminCannotSearchReceptionPatientProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/reception/profiles?phone=0912345678")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    } // admin tra cứu danh sách hồ sơ

    @Test
    void coordinatorCannotSearchReceptionPatientProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/reception/profiles?phone=0912345678")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isForbidden());
    } //người điều phối tra cứu danh sách hồ sơ
    
    // Nhóm xử lý ngoại lệ
    @Test
    void receptionistCanConfirmArrival() throws Exception {
        when(service.checkIn(eq(APPOINTMENT_ID), any(), eq(STAFF_ID.toString()))).thenReturn(responseWithTicket());

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
        when(service.leaveBeforeExam(eq(APPOINTMENT_ID), eq("Người bệnh bận việc đột xuất"), eq(STAFF_ID.toString())))
                .thenReturn(responseWithLeftTicket());

        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/leave")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Người bệnh bận việc đột xuất\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(5))
                .andExpect(jsonPath("$.queueStatus").value("LEFT_BEFORE_EXAM"));
    }

    // Nhóm định dạng dữ liệu đầu vào
    @Test
    void receptionistCannotUseExceptionCheckInWithoutReason() throws Exception {
        mockMvc.perform(post("/api/v1/reception/appointments/" + APPOINTMENT_ID + "/check-in")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"roomCode\":\"NOI-01\"}"))
                .andExpect(status().isBadRequest());
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

    // Nhóm tiếp nhận không có lịch (FR-REC-03)

    @Test
    void receptionistCanCreateWalkInFromExistingPatientAccount() throws Exception {
        when(service.createWalkIn(any(), eq(STAFF_ID.toString()))).thenReturn(responseWithTicket());
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
    void receptionistCanCreateTemporaryProfileWithoutAnAccount() throws Exception {
        when(service.createTemporaryProfile(any())).thenReturn(new ReceptionPatientProfileResponse(
                UUID.randomUUID(), "Nguyễn Văn Tạm", "Tạm tại quầy", LocalDate.of(1990, 1, 1), false,
                null, false));

        mockMvc.perform(post("/api/v1/reception/temporary-profiles")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                        .contentType("application/json")
                        .content("{\"phone\":\"0912345678\",\"fullName\":\"Nguyễn Văn Tạm\","
                                + "\"dateOfBirth\":\"1990-01-01\",\"gender\":\"Nam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Tạm"))
                .andExpect(jsonPath("$.accountStatus").doesNotExist());
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

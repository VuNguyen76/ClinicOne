package com.clinicone.doctor;

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

import java.time.DayOfWeek;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorManagementController.class)
@Import({SecurityConfig.class, DoctorManagementControllerTest.MockBeans.class})
class DoctorManagementControllerTest {
    private static final UUID DOCTOR_ID = UUID.fromString("9d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID ROOM_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorManagementService service;

    @Test
    void adminCanCreateDoctorAccount() throws Exception {
        when(service.createDoctor(any())).thenReturn(new DoctorAccountResponse(DOCTOR_ID, "bs.an",
                "Bác sĩ Nguyễn An", null, null, null, null, false, false, null));

        mockMvc.perform(post("/api/v1/admin/doctors")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"username\":\"bs.an\",\"fullName\":\"Bác sĩ Nguyễn An\",\"password\":\"doctor123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bs.an"))
                .andExpect(jsonPath("$.assigned").value(false));
    }

    @Test
    void adminCanAssignDoctorAndCreateWorkingHours() throws Exception {
        when(service.assign(eq(DOCTOR_ID), any())).thenReturn(profile());
        when(service.addSchedule(eq(DOCTOR_ID), any())).thenReturn(schedule());

        mockMvc.perform(put("/api/v1/admin/doctors/" + DOCTOR_ID + "/assignment")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"specialty\":\"Khám Tổng Quát\",\"roomId\":\"" + ROOM_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialty").value("Khám Tổng Quát"));

        mockMvc.perform(post("/api/v1/admin/doctors/" + DOCTOR_ID + "/schedules")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"slotDurationMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"));
    }

    @Test
    void doctorCannotConfigureAnotherDoctor() throws Exception {
        mockMvc.perform(get("/api/v1/admin/doctors")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(DOCTOR_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static DoctorProfileResponse profile() {
        return new DoctorProfileResponse(DOCTOR_ID, "doctor", "Bác sĩ Nguyễn An", "Khám Tổng Quát",
                ROOM_ID, "NOI-01", "Phòng Nội 01", true, null);
    }

    private static DoctorScheduleResponse schedule() {
        return new DoctorScheduleResponse(UUID.randomUUID(), DOCTOR_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        DoctorManagementService doctorManagementService() {
            return mock(DoctorManagementService.class);
        }
    }
}

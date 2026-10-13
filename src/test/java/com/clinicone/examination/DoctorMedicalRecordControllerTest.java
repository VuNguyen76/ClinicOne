package com.clinicone.examination;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorMedicalRecordController.class)
@Import({SecurityConfig.class, DoctorMedicalRecordControllerTest.MockBeans.class})
class DoctorMedicalRecordControllerTest {
    private static final UUID DOCTOR_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID RECORD_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorMedicalRecordService service;

    @Test
    void doctorCanListOwnMedicalHistory() throws Exception {
        when(service.listHistory(eq(DOCTOR_ID.toString()), any())).thenReturn(
                new DoctorMedicalRecordHistoryPage(List.of(record()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/doctor/medical-records?search=Mai&from=2026-10-01&to=2026-10-13")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(RECORD_ID.toString()))
                .andExpect(jsonPath("$.items[0].patientName").value("Nguyễn Thị Mai"))
                .andExpect(jsonPath("$.items[0].prescriptionLines").isEmpty());
    }

    @Test
    void patientCannotUseDoctorHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/medical-records")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCanOpenOnlyTheRecordResolvedByDoctorScopedService() throws Exception {
        when(service.get(DOCTOR_ID.toString(), RECORD_ID)).thenReturn(record());

        mockMvc.perform(get("/api/v1/doctor/medical-records/" + RECORD_ID)
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Theo dõi định kỳ"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(DOCTOR_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static DoctorMedicalRecordResponse record() {
        return new DoctorMedicalRecordResponse(RECORD_ID, "CL-20261012-0001", "Nguyễn Thị Mai",
                "Khám Tim Mạch", LocalDate.of(2026, 10, 12), "Tái khám", "Sinh hiệu ổn",
                "Theo dõi định kỳ", "Ổn định", "Duy trì điều trị", null, List.of(), null,
                null, null, Instant.parse("2026-10-12T02:30:00Z"), "120/80", 72, null, 98,
                null, null, null);
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        DoctorMedicalRecordService doctorMedicalRecordService() {
            return mock(DoctorMedicalRecordService.class);
        }
    }
}

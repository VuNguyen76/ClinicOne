package com.clinicone.examination;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicalRecordController.class)
@Import({SecurityConfig.class, MedicalRecordControllerTest.MockBeans.class})
class MedicalRecordControllerTest {
    private static final UUID PATIENT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID RECORD_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicalRecordService service;

    @Test
    void patientCanListSignedRecords() throws Exception {
        when(service.listHistory(eq(PATIENT_ID.toString()), any())).thenReturn(new MedicalRecordHistoryPage(
                List.of(record()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/medical-records?profileId=" + UUID.randomUUID()
                        + "&from=2026-08-01&to=2026-08-20&page=0&size=20")
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(RECORD_ID.toString()))
                .andExpect(jsonPath("$.items[0].signedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void patientCanOpenOwnSignedRecord() throws Exception {
        when(service.get(PATIENT_ID.toString(), RECORD_ID.toString())).thenReturn(record());

        mockMvc.perform(get("/api/v1/medical-records/" + RECORD_ID)
                        .with(authentication(authenticated("ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Đau đầu do căng thẳng"));
    }

    @Test
    void staffCannotReadPatientMedicalRecords() throws Exception {
        mockMvc.perform(get("/api/v1/medical-records")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(PATIENT_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static MedicalRecordResponse record() {
        return new MedicalRecordResponse(RECORD_ID, UUID.randomUUID(), "CL-20260807-0009", "Bác sĩ Nguyễn An",
                "Đau đầu", "Mạch ổn", "Đau đầu do căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, null,
                Instant.parse("2026-08-07T09:30:00Z"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        MedicalRecordService medicalRecordService() {
            return mock(MedicalRecordService.class);
        }
    }
}

package com.clinicone.examination;

import com.clinicone.config.SecurityConfig;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorExaminationController.class)
@Import({SecurityConfig.class, DoctorExaminationControllerTest.MockBeans.class})
class DoctorExaminationControllerTest {
    private static final UUID STAFF_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID TICKET_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorExaminationService service;

    @Test
    void doctorCanOpenExaminationWorkspace() throws Exception {
        when(service.open(TICKET_ID, STAFF_ID.toString())).thenReturn(response());

        mockMvc.perform(get("/api/v1/doctor/examinations/" + TICKET_ID)
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("Nguyễn Thanh Vũ"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void doctorCanStartAnExaminationWithAnIdempotencyKey() throws Exception {
        when(service.start(TICKET_ID, STAFF_ID.toString(), "start-visit-1")).thenReturn(response());

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/start")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .header("Idempotency-Key", "start-visit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void doctorCanSaveDraft() throws Exception {
        when(service.saveDraft(eq(TICKET_ID), eq(STAFF_ID.toString()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());

        mockMvc.perform(put("/api/v1/doctor/examinations/" + TICKET_ID + "/draft")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Đau đầu\",\"examinationNotes\":\"Mạch ổn\",\"recordVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Đau đầu"));
    }

    @Test
    void doctorCanSaveTwoThousandCharactersInEachRequiredClinicalField() throws Exception {
        when(service.saveDraft(eq(TICKET_ID), eq(STAFF_ID.toString()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());
        String text = "a".repeat(2000);

        mockMvc.perform(put("/api/v1/doctor/examinations/" + TICKET_ID + "/draft")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"" + text + "\",\"examinationNotes\":\"" + text
                                + "\",\"diagnosis\":\"" + text + "\",\"conclusion\":\"" + text
                                + "\",\"recordVersion\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void doctorCannotSaveMoreThanTwoThousandCharactersInARequiredClinicalField() throws Exception {
        String text = "a".repeat(2001);
        clearInvocations(service);

        mockMvc.perform(put("/api/v1/doctor/examinations/" + TICKET_ID + "/draft")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"" + text + "\",\"recordVersion\":0}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).saveDraft(any(), any(), any());
    }

    @Test
    void doctorCanSignCompletedExamination() throws Exception {
        when(service.sign(eq(TICKET_ID), eq(STAFF_ID.toString()), org.mockito.ArgumentMatchers.any(), eq("sign-visit-1")))
                .thenReturn(signedResponse());

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/sign")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .header("Idempotency-Key", "sign-visit-1")
                        .contentType("application/json")
                        .content("{\"reason\":\"Đau đầu\",\"examinationNotes\":\"Mạch ổn\",\"diagnosis\":\"Đau đầu căng thẳng\",\"conclusion\":\"Theo dõi thêm\",\"recordVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.signedAt").isNotEmpty());
    }

    @Test
    void doctorMustProvideAnIdempotencyKeyToSign() throws Exception {
        clearInvocations(service);
        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/sign")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Đau đầu\",\"examinationNotes\":\"Mạch ổn\",\"diagnosis\":\"Đau đầu căng thẳng\",\"conclusion\":\"Theo dõi thêm\",\"recordVersion\":0}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).sign(any(), any(), any(), any());
    }

    @Test
    void doctorCanStopAnInProgressExaminationWithAnOperationalReason() throws Exception {
        when(service.stop(eq(TICKET_ID), eq(STAFF_ID.toString()), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/stop")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Người bệnh cần rời phòng khám ngay.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void doctorCannotStopAnExaminationWithAnIncompleteReason() throws Exception {
        clearInvocations(service);

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/stop")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Ngắn\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).stop(any(), any(), any());
    }

    @Test
    void doctorCanReturnAWrongProfileToTheWaitingQueueWithAReason() throws Exception {
        when(service.wrongProfile(eq(TICKET_ID), eq(STAFF_ID.toString()), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/wrong-profile")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Bác sĩ phát hiện đang mở nhầm hồ sơ người bệnh.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void doctorCannotReturnWrongProfileWithoutAnOperationalReason() throws Exception {
        clearInvocations(service);

        mockMvc.perform(post("/api/v1/doctor/examinations/" + TICKET_ID + "/wrong-profile")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Nhầm\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).wrongProfile(any(), any(), any());
    }

    @Test
    void doctorGetsAReadableConflictWhenAnotherTabSavedTheRecordFirst() throws Exception {
        when(service.saveDraft(eq(TICKET_ID), eq(STAFF_ID.toString()), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(MedicalRecord.class, TICKET_ID));

        mockMvc.perform(put("/api/v1/doctor/examinations/" + TICKET_ID + "/draft")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType("application/json")
                        .content("{\"reason\":\"Đau đầu\",\"recordVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDICAL_RECORD_VERSION_CONFLICT"));
    }

    @Test
    void nonDoctorCannotOpenDoctorWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/doctor/examinations/" + TICKET_ID)
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(STAFF_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static DoctorExaminationResponse response() {
        return new DoctorExaminationResponse(TICKET_ID, UUID.randomUUID(), UUID.randomUUID(), 5,
                "Phòng Nội tổng quát 01", "CL-20260806-1234", "Nội tổng quát", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 6), LocalTime.of(9, 0), "Nguyễn Thanh Vũ", LocalDate.of(2005, 6, 7),
                "Nam", "0862764830", "Đau đầu", "Mạch ổn", "", "", "", "", null,
                "IN_PROGRESS", null);
    }

    private static DoctorExaminationResponse signedResponse() {
        return new DoctorExaminationResponse(TICKET_ID, UUID.randomUUID(), UUID.randomUUID(), 5,
                "Phòng Nội tổng quát 01", "CL-20260806-1234", "Nội tổng quát", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 6), LocalTime.of(9, 0), "Nguyễn Thanh Vũ", LocalDate.of(2005, 6, 7),
                "Nam", "0862764830", "Đau đầu", "Mạch ổn", "Đau đầu căng thẳng", "Theo dõi thêm", "", "", null,
                "COMPLETED", Instant.parse("2026-08-07T09:30:00Z"));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        DoctorExaminationService doctorExaminationService() {
            return mock(DoctorExaminationService.class);
        }
    }
}

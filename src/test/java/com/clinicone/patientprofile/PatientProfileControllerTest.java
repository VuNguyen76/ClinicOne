package com.clinicone.patientprofile;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientProfileController.class)
@Import({SecurityConfig.class, PatientProfileControllerTest.MockBeans.class})
class PatientProfileControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString("9d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID ACCOUNT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientProfileService service;

    @Test
    void receptionistCanUpdateMissingProfileData() throws Exception {
        // Giả lập logic trả về thành công từ Service
        when(service.updateMissingDataByReceptionist(eq(PROFILE_ID.toString()), any(ReceptionUpdatePatientProfileRequest.class)))
                .thenReturn(new PatientProfileResponse(
                        PROFILE_ID, "Lê Văn C", "Bản thân", LocalDate.of(1995, 10, 10), "Nam",
                        null, null, null, null, "Hà Nội",
                        null, null, null, null, null, null, null, true
                ));

        // Dùng chuỗi JSON trực tiếp giống phong cách của nhóm trưởng
        String requestJson = "{\"fullName\":\"Lê Văn C\",\"relationship\":\"Bản thân\",\"dateOfBirth\":\"1995-10-10\",\"gender\":\"Nam\",\"address\":\"Hà Nội\"}";

        // Gọi API với quyền Lễ tân
        mockMvc.perform(patch("/api/v1/patient-profiles/" + PROFILE_ID + "/reception-update")
                .with(authentication(authenticated("ROLE_RECEPTIONIST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Văn C"));
    }

    @Test
    void patientCannotUseReceptionUpdateEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/patient-profiles/" + PROFILE_ID + "/reception-update")
                .with(authentication(authenticated("ROLE_PATIENT")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Lê Văn C\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotUseReceptionUpdateEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/patient-profiles/" + PROFILE_ID + "/reception-update")
                .with(authentication(authenticated("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Lê Văn C\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotUseReceptionUpdateEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/patient-profiles/" + PROFILE_ID + "/reception-update")
                .with(authentication(authenticated("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Lê Văn C\"}"))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(ACCOUNT_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        PatientProfileService patientProfileService() {
            return mock(PatientProfileService.class);
        }
    }
}

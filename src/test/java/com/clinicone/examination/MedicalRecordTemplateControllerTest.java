package com.clinicone.examination;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicalRecordTemplateController.class)
@Import({SecurityConfig.class, MedicalRecordTemplateControllerTest.MockBeans.class})
class MedicalRecordTemplateControllerTest {
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicalRecordTemplateService service;

    @Test
    void administratorCanViewTemplatesButCannotCreateUpdateOrDeactivateThem() throws Exception {
        when(service.list(true, null, null)).thenReturn(List.of(response()));
        mockMvc.perform(get("/api/v1/medical-record-templates")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Khám tổng quát"));

        mockMvc.perform(post("/api/v1/medical-record-templates")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/medical-record-templates/{id}", TEMPLATE_ID)
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/medical-record-templates/{id}", TEMPLATE_ID)
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanCreateTemplates() throws Exception {
        when(service.create(any(), eq("staff-id"))).thenReturn(response());

        mockMvc.perform(post("/api/v1/medical-record-templates")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Khám tổng quát"));
    }

    private static MedicalRecordTemplateResponse response() {
        return new MedicalRecordTemplateResponse(TEMPLATE_ID, "GEN", "Khám tổng quát", "Nội tổng quát",
                null, "Mẫu", "{}", true, "coordinator", Instant.now());
    }

    private static String requestBody() {
        return "{\"code\":\"GEN\",\"name\":\"Khám tổng quát\",\"specialty\":\"Nội tổng quát\",\"fieldDefinition\":\"{\\\"fields\\\":[]}\"}";
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        MedicalRecordTemplateService medicalRecordTemplateService() {
            return mock(MedicalRecordTemplateService.class);
        }
    }
}

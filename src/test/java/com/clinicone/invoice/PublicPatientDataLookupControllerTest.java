package com.clinicone.invoice;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicPatientDataLookupController.class)
@org.springframework.context.annotation.Import({SecurityConfig.class, PublicPatientDataLookupControllerTest.MockBeans.class})
class PublicPatientDataLookupControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private PublicPatientDataLookupService service;

    @Test
    void anyoneCanLookupWithPhoneAndOtpAndResponseIsNotCached() throws Exception {
        when(service.lookup(anyString(), anyString())).thenReturn(new PublicPatientDataLookupResponse(List.of()));

        mockMvc.perform(post("/api/v1/public/patient-data/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0862764830\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.profiles").isEmpty());
    }

    @Test
    void rejectsMalformedOtpInput() throws Exception {
        mockMvc.perform(post("/api/v1/public/patient-data/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0862764830\",\"code\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class MockBeans {
        @Bean PublicPatientDataLookupService lookupService() { return mock(PublicPatientDataLookupService.class); }
    }
}

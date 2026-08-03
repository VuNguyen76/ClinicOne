package com.clinicone.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({AuthService.class, SecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidVietnamesePhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/check-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("OTP"));
    }

    @Test
    void rejectsPhoneWithWrongLengthOrPrefix() throws Exception {
        mockMvc.perform(post("/api/v1/auth/check-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"912345678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/check-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

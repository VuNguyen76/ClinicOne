package com.clinicone.queue;

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

@WebMvcTest(ClinicRoomController.class)
@Import({SecurityConfig.class, ClinicRoomControllerTest.MockBeans.class})
class ClinicRoomControllerTest {
    private static final UUID ROOM_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicRoomService service;

    @Test
    void coordinatorCanViewRooms() throws Exception {
        when(service.list()).thenReturn(List.of(roomResponse(true)));

        mockMvc.perform(get("/api/v1/rooms")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NOI-01"));
    }

    @Test
    void doctorCanViewRoomsForQueueSelection() throws Exception {
        when(service.list()).thenReturn(List.of(roomResponse(true)));

        mockMvc.perform(get("/api/v1/rooms")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NOI-01"));
    }

    @Test
    void receptionistCanViewRoomsForQueueSelection() throws Exception {
        when(service.list()).thenReturn(List.of(roomResponse(true)));

        mockMvc.perform(get("/api/v1/rooms")
                        .with(authentication(authenticated("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NOI-01"));
    }

    @Test
    void coordinatorCannotCreateRoom() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOI-01\",\"name\":\"Phòng Nội 01\",\"specialty\":\"Nội tổng quát\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesRoom() throws Exception {
        when(service.create(any())).thenReturn(roomResponse(true));

        mockMvc.perform(post("/api/v1/rooms")
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOI-01\",\"name\":\"Phòng Nội 01\",\"specialty\":\"Nội tổng quát\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminUpdatesRoom() throws Exception {
        when(service.update(eq(ROOM_ID), any())).thenReturn(roomResponse(true));

        mockMvc.perform(put("/api/v1/rooms/" + ROOM_ID)
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOI-02\",\"name\":\"Phòng Nội 02\",\"specialty\":\"Nội tổng quát\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOI-01"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-1", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static ClinicRoomResponse roomResponse(boolean active) {
        return new ClinicRoomResponse(ROOM_ID, "NOI-01", "Phòng Nội 01", "Nội tổng quát", active);
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ClinicRoomService clinicRoomService() {
            return mock(ClinicRoomService.class);
        }
    }
}

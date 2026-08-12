package com.clinicone.auth;

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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffManagementController.class)
@Import({SecurityConfig.class, StaffManagementControllerTest.MockBeans.class})
class StaffManagementControllerTest {
    private static final UUID STAFF_ID = UUID.fromString("0b6f0f1a-11cd-4c96-98f8-2d46c9eae2c1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffManagementService service;

    @Autowired
    private com.clinicone.audit.AccessAuditService accessAuditService;

    @Test
    void adminCanListAndLockStaffAccount() throws Exception {
        StaffAccountResponse response = new StaffAccountResponse(STAFF_ID, "bs.an", "Bác sĩ An",
                StaffRole.DOCTOR, AccountStatus.ACTIVE);
        when(service.list()).thenReturn(List.of(response));
        when(service.lock(any(), any())).thenReturn(response.withStatus(AccountStatus.LOCKED));

        mockMvc.perform(get("/api/v1/admin/staff")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bs.an"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/staff/" + STAFF_ID + "/lock")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        verify(accessAuditService).record("STAFF_LOCK", STAFF_ID.toString(), "SUCCESS", "/api/v1/admin/staff/{id}/lock", "127.0.0.1");
    }

    @Test
    void doctorCannotManageStaffAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(STAFF_ID.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        StaffManagementService staffManagementService() {
            return mock(StaffManagementService.class);
        }

        @Bean
        com.clinicone.audit.AccessAuditService accessAuditService() {
            return mock(com.clinicone.audit.AccessAuditService.class);
        }
    }
}

package com.clinicone.reconciliation;

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

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
@Import({SecurityConfig.class, ReconciliationControllerTest.MockBeans.class})
class ReconciliationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanCloseReconciliation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reconciliations/{id}/close", UUID.randomUUID())
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"action\":\"RETRY_BUSINESS_ACTION\",\"referenceType\":\"INCIDENT\",\"referenceValue\":\"INC-TEST\",\"resultNote\":\"Đã kiểm tra lại dữ liệu.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void coordinatorCannotReadOpenOrCloseReconciliation() throws Exception {
        UUID incidentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/admin/reconciliations")
                        .with(authentication(authenticated("ROLE_COORDINATOR"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/reconciliations")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType("application/json")
                        .content("{\"entityType\":\"QUEUE_TICKET\",\"entityId\":\"" + UUID.randomUUID() + "\",\"reason\":\"Thiếu dữ liệu hàng đợi\",\"assignee\":\"coordinator\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/reconciliations/{id}/close", incidentId)
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType("application/json")
                        .content("{\"action\":\"RETRY_BUSINESS_ACTION\",\"referenceType\":\"INCIDENT\",\"referenceValue\":\"INC-TEST\",\"resultNote\":\"Đã kiểm tra lại\"}"))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("staff-id", null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        ReconciliationService reconciliationService() { return mock(ReconciliationService.class); }
    }
}

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
@Import({SecurityConfig.class, ReconciliationControllerTest.MockBeans.class})
class ReconciliationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReconciliationService service;

    @Test
    void adminCannotCloseReconciliation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reconciliations/{id}/close", UUID.randomUUID())
                        .with(authentication(authenticated("ROLE_ADMIN")))
                        .contentType("application/json")
                        .content("{\"action\":\"RETRY_BUSINESS_ACTION\",\"referenceType\":\"INCIDENT\",\"referenceValue\":\"INC-TEST\",\"resultNote\":\"Đã kiểm tra lại dữ liệu.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanOpenReconciliation() throws Exception {
        when(service.open(any())).thenReturn(new ReconciliationResponse(UUID.randomUUID(), "INC-TEST", "QUEUE_TICKET",
                UUID.randomUUID(), null, "Thiếu dữ liệu hàng đợi", "coordinator", ReconciliationStatus.OPEN,
                null, null, null, null, null, null, null));
        mockMvc.perform(post("/api/v1/admin/reconciliations")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType("application/json")
                        .content("{\"entityType\":\"QUEUE_TICKET\",\"entityId\":\"" + UUID.randomUUID() + "\",\"reason\":\"Thiếu dữ liệu hàng đợi\",\"assignee\":\"coordinator\"}"))
                .andExpect(status().isCreated());
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

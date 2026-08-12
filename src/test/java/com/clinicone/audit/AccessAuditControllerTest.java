package com.clinicone.audit;

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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessAuditController.class)
@Import({SecurityConfig.class, AccessAuditControllerTest.MockBeans.class})
class AccessAuditControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessAuditService service;

    @Test
    void onlyAdministratorCanReadAccessAudit() throws Exception {
        when(service.list(null, null, null, null, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/access-audit")
                        .with(authentication(authenticated(UUID.randomUUID().toString(), "ROLE_ADMIN"))))
                .andExpect(status().isOk());
        String actor = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/admin/access-audit")
                        .with(authentication(authenticated(actor, "ROLE_COORDINATOR"))))
                .andExpect(status().isForbidden());
        verify(service).record("ACCESS_DENIED", actor, "FAILED", "/api/v1/admin/access-audit", "127.0.0.1");
    }

    private static UsernamePasswordAuthenticationToken authenticated(String actor, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(actor, null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AccessAuditService accessAuditService() { return mock(AccessAuditService.class); }
    }
}

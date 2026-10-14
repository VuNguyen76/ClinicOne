package com.clinicone.invoice;

import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@org.springframework.context.annotation.Import({SecurityConfig.class, InvoiceControllerTest.MockBeans.class})
class InvoiceControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private InvoiceService service;

    @Test
    void patientCanReadOwnInvoiceList() throws Exception {
        when(service.listForPatient("patient-1")).thenReturn(List.of(receipt()));

        mockMvc.perform(get("/api/v1/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticated("patient-1", "ROLE_PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceCode").value("INV-1"));
    }

    @Test
    void publicReceiptRouteDoesNotAllowAppointmentCodeLookup() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/appointments/APT-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCanCreatePaymentReceiptForAppointment() throws Exception {
        when(service.createPaymentReceiptForAppointment("APT-1", "staff-1")).thenReturn(receipt());

        mockMvc.perform(post("/api/v1/invoices/appointments/APT-1")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticated("staff-1", "ROLE_RECEPTIONIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceCode").value("INV-1"));
    }

    @Test
    void patientCannotRecordPayment() throws Exception {
        mockMvc.perform(post("/api/v1/invoices/INV-1/payment")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticated("patient-1", "ROLE_PATIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250000,\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    private static InvoiceResponse receipt() {
        return new InvoiceResponse(UUID.randomUUID(), "INV-1", "APT-1", "Patient A", "0912345678",
                "Doctor A", "Nội tổng quát", 250_000, 0, 0, 0, 250_000, 0, null,
                InvoiceStatus.PENDING, "staff-1", Instant.parse("2026-10-12T02:00:00Z"), null, List.of());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String user, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean InvoiceService invoiceService() { return mock(InvoiceService.class); }
    }
}

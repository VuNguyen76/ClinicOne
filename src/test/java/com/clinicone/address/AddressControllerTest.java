package com.clinicone.address;

import com.clinicone.audit.AccessAuditService;
import com.clinicone.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, AddressControllerTest.MockBeans.class})
class AddressControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AddressService addressService;

    @Test
    void provincesAreAvailableWithoutForwardingBrowserCredentials() throws Exception {
        when(addressService.provinces(1, 100)).thenReturn(List.of(
                new AddressUnitResponse("Tây Ninh", "72", "Tỉnh", "tỉnh", "tay_ninh", null, null)));

        mockMvc.perform(get("/api/v1/addresses/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tây Ninh"))
                .andExpect(jsonPath("$[0].code").value("72"));
    }

    @Test
    void rejectsInvalidAddressCode() throws Exception {
        mockMvc.perform(get("/api/v1/addresses/provinces/not%20a%20code/districts"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedPageLimit() throws Exception {
        mockMvc.perform(get("/api/v1/addresses/provinces?page=1&limit=101"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        AddressService addressService() {
            return mock(AddressService.class);
        }

        @Bean
        AccessAuditService accessAuditService() {
            return mock(AccessAuditService.class);
        }
    }
}

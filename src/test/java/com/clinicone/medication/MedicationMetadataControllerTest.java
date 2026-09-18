package com.clinicone.medication;

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicationMetadataController.class)
@Import({SecurityConfig.class, MedicationMetadataControllerTest.MockBeans.class})
class MedicationMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicationUnitRepository unitRepository;

    @Autowired
    private MedicationDosageRepository dosageRepository;

    @Test
    void staffCanListMedicationUnitsAndDosages() throws Exception {
        MedicationUnit unit = new MedicationUnit("UNIT-VIEN", "Viên", "Viên nén", 1);
        when(unitRepository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(unit));

        MedicationDosage dosage = new MedicationDosage("DOS-VIEN-2X", "Viên", "1 viên/lần x 2 lần/ngày", "Mẫu", 1);
        when(dosageRepository.findByActiveTrueAndUnitNameIgnoreCaseOrderBySortOrderAsc("Viên"))
                .thenReturn(List.of(dosage));

        mockMvc.perform(get("/api/v1/medication-units").with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Viên"));

        mockMvc.perform(get("/api/v1/medication-dosages").param("unit", "Viên").with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dosageFormat").value("1 viên/lần x 2 lần/ngày"));
    }

    private UsernamePasswordAuthenticationToken authenticated(String role) {
        return new UsernamePasswordAuthenticationToken("staff", "n/a", List.of(new SimpleGrantedAuthority(role)));
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        MedicationUnitRepository unitRepository() {
            return mock(MedicationUnitRepository.class);
        }

        @Bean
        MedicationDosageRepository dosageRepository() {
            return mock(MedicationDosageRepository.class);
        }
    }
}

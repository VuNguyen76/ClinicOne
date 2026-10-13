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

    @Test
    void inactiveDosagesCanBeListedForSelectedUnitWhenRequested() throws Exception {
        MedicationDosage active = new MedicationDosage("DOS-VIEN-1X", "Viên", "1 viên/lần", "Đang dùng", 1);
        MedicationDosage inactive = new MedicationDosage("DOS-VIEN-2X-OLD", "Viên", "1 viên/lần x 2 lần/ngày", "Ngừng dùng", 2);
        inactive.setActive(false);
        when(dosageRepository.findAllByUnitNameIgnoreCaseOrderBySortOrderAsc("Viên"))
                .thenReturn(List.of(active, inactive));

        mockMvc.perform(get("/api/v1/medication-dosages")
                        .param("unit", "Viên")
                        .param("activeOnly", "false")
                        .with(authentication(authenticated("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DOS-VIEN-1X"))
                .andExpect(jsonPath("$[1].code").value("DOS-VIEN-2X-OLD"))
                .andExpect(jsonPath("$[1].active").value(false));
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

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
import org.springframework.http.MediaType;
import java.util.Optional;
import java.util.UUID;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void doctorCannotRequestInactiveMedicationMetadata() throws Exception {
        when(unitRepository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of());
        when(dosageRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/medication-units").param("activeOnly", "false")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/medication-dosages").param("activeOnly", "false")
                        .with(authentication(authenticated("ROLE_DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(unitRepository).findByActiveTrueOrderBySortOrderAscNameAsc();
        verify(dosageRepository).findByActiveTrueOrderBySortOrderAsc();
    }

    @Test
    void coordinatorCanCreateMedicationUnitAndDosageMetadata() throws Exception {
        when(unitRepository.existsByCodeIgnoreCase("UNIT-CAP")).thenReturn(false);
        when(unitRepository.existsByNameIgnoreCase("Capsule")).thenReturn(false);
        when(unitRepository.findByNameIgnoreCase("Viên"))
                .thenReturn(java.util.Optional.of(new MedicationUnit("UNIT-VIEN", "Viên", "", 1)));
        when(dosageRepository.existsByCodeIgnoreCase("DOS-CAP-1X")).thenReturn(false);
        doAnswer(invocation -> invocation.getArgument(0)).when(unitRepository).save(org.mockito.ArgumentMatchers.any(MedicationUnit.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(dosageRepository).save(org.mockito.ArgumentMatchers.any(MedicationDosage.class));

        mockMvc.perform(post("/api/v1/medication-units")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNIT-CAP","name":"Capsule","description":"Viên nang","sortOrder":20}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("UNIT-CAP"));

        mockMvc.perform(post("/api/v1/medication-dosages")
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DOS-CAP-1X","unitName":"Viên","dosageFormat":"1 viên/lần mỗi ngày","description":"","sortOrder":20}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DOS-CAP-1X"));

        verify(unitRepository).save(org.mockito.ArgumentMatchers.any(MedicationUnit.class));
        verify(dosageRepository).save(org.mockito.ArgumentMatchers.any(MedicationDosage.class));
    }

    @Test
    void doctorCannotCreateMedicationMetadata() throws Exception {
        mockMvc.perform(post("/api/v1/medication-units")
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNIT-CAP","name":"Capsule","description":"","sortOrder":20}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanEditAndSafelyDeactivateMedicationCatalogMetadata() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID dosageId = UUID.randomUUID();
        MedicationUnit unit = new MedicationUnit("UNIT-VIEN", "Viên", "", 1);
        MedicationDosage dosage = new MedicationDosage("DOS-VIEN-1X", "Viên", "1 viên/lần", "", 1);
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(dosageRepository.findById(dosageId)).thenReturn(Optional.of(dosage));
        when(unitRepository.existsByCodeIgnoreCaseAndIdNot("UNIT-TAB", unitId)).thenReturn(false);
        when(unitRepository.existsByNameIgnoreCaseAndIdNot("Viên nén", unitId)).thenReturn(false);
        when(dosageRepository.existsByCodeIgnoreCaseAndIdNot("DOS-VIEN-2X", dosageId)).thenReturn(false);
        when(unitRepository.findByNameIgnoreCase("Viên nén")).thenReturn(Optional.of(new MedicationUnit("UNIT-TAB", "Viên nén", "", 2)));
        doAnswer(invocation -> invocation.getArgument(0)).when(unitRepository).save(org.mockito.ArgumentMatchers.any(MedicationUnit.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(dosageRepository).save(org.mockito.ArgumentMatchers.any(MedicationDosage.class));

        mockMvc.perform(put("/api/v1/medication-units/{id}", unitId)
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"UNIT-TAB\",\"name\":\"Viên nén\",\"description\":\"Dạng viên\",\"sortOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Viên nén"));

        mockMvc.perform(put("/api/v1/medication-dosages/{id}", dosageId)
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DOS-VIEN-2X\",\"unitName\":\"Viên nén\",\"dosageFormat\":\"2 viên/ngày\",\"description\":\"\",\"sortOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dosageFormat").value("2 viên/ngày"));

        mockMvc.perform(patch("/api/v1/medication-units/{id}/active", unitId)
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/v1/medication-dosages/{id}/active", dosageId)
                        .with(authentication(authenticated("ROLE_COORDINATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void doctorCannotEditOrDeactivateMedicationMetadata() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(put("/api/v1/medication-units/{id}", id)
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"UNIT-TAB\",\"name\":\"Viên nén\",\"description\":\"\",\"sortOrder\":2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/medication-dosages/{id}/active", id)
                        .with(authentication(authenticated("ROLE_DOCTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
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

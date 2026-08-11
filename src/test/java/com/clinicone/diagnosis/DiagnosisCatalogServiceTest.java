package com.clinicone.diagnosis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosisCatalogServiceTest {
    private DiagnosisCatalogRepository repository;
    private DiagnosisCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(DiagnosisCatalogRepository.class);
        service = new DiagnosisCatalogService(repository);
    }

    @Test
    void suggestsOnlyActiveDiagnosesAfterTwoCharactersAndLimitsResults() throws Exception {
        assertThat(service.suggestions("d")).isEmpty();
        verify(repository, never()).findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc("d");

        DiagnosisCatalog diagnosis = DiagnosisCatalog.create("HEADACHE_TENSION", "Đau đầu căng thẳng");
        UUID id = UUID.randomUUID();
        setId(diagnosis, id);
        when(repository.findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc("đau"))
                .thenReturn(List.of(diagnosis));

        assertThat(service.suggestions(" đau "))
                .containsExactly(new DiagnosisCatalogResponse(id, "HEADACHE_TENSION", "Đau đầu căng thẳng", true));
    }

    @Test
    void createsUppercaseCatalogCodesAndRejectsDuplicates() {
        when(repository.existsByCodeIgnoreCase("HEADACHE_TENSION")).thenReturn(false, true);
        DiagnosisCatalog created = DiagnosisCatalog.create("HEADACHE_TENSION", "Đau đầu căng thẳng");
        UUID id = UUID.randomUUID();
        try {
            setId(created, id);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        when(repository.save(org.mockito.ArgumentMatchers.any(DiagnosisCatalog.class))).thenReturn(created);

        assertThat(service.create(new CreateDiagnosisCatalogRequest("headache_tension", " Đau đầu căng thẳng ")))
                .isEqualTo(new DiagnosisCatalogResponse(id, "HEADACHE_TENSION", "Đau đầu căng thẳng", true));
        assertThatThrownBy(() -> service.create(new CreateDiagnosisCatalogRequest("headache_tension", "Đau đầu căng thẳng")))
                .isInstanceOf(com.clinicone.auth.AuthException.class)
                .hasMessageContaining("đã tồn tại");
    }

    private static void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}

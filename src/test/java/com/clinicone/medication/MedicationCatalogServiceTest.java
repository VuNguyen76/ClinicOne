package com.clinicone.medication;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicationCatalogServiceTest {
    private MedicationRepository repository;
    private MedicationCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(MedicationRepository.class);
        service = new MedicationCatalogService(repository);
    }

    @Test
    void onlySearchesAfterTwoCharactersAndReturnsActiveMatches() throws Exception {
        assertThat(service.suggestions("p")).isEmpty();
        verify(repository, never()).findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc("p");

        Medication medication = Medication.create("PCM500", "Paracetamol 500 mg");
        UUID id = UUID.randomUUID();
        setId(medication, id);
        when(repository.findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc("para"))
                .thenReturn(List.of(medication));

        assertThat(service.suggestions(" para ")).containsExactly(new MedicationResponse(id, "PCM500", "Paracetamol 500 mg", true));
    }

    @Test
    void rejectsAnInactiveOrMissingMedicationWhenItIsSelected() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActive(id))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("không còn trong danh mục");
    }

    private static void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}

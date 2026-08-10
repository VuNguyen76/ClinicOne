package com.clinicone.reason;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReasonCatalogServiceTest {
    private ReasonCatalogRepository repository;
    private ReasonCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReasonCatalogRepository.class);
        service = new ReasonCatalogService(repository);
    }

    @Test
    void resolvesOnlyActiveReasonByTypeAndCode() {
        ReasonCatalog reason = ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "SCHEDULE_CHANGE", "Đổi kế hoạch");
        setId(reason, UUID.randomUUID());
        when(repository.findByTypeAndCodeIgnoreCaseAndActiveTrue(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "SCHEDULE_CHANGE")).thenReturn(Optional.of(reason));

        assertEquals("Đổi kế hoạch", service.requireActive(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "SCHEDULE_CHANGE").getLabel());
    }

    @Test
    void rejectsDeactivatingLastThreeCancellationReasons() {
        ReasonCatalog reason = ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "SCHEDULE_CHANGE", "Đổi kế hoạch");
        setId(reason, UUID.randomUUID());
        when(repository.findById(any(UUID.class))).thenReturn(Optional.of(reason));
        when(repository.countByTypeAndActiveTrue(ReasonCatalogType.APPOINTMENT_CANCELLATION)).thenReturn(3L);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.setActive(reason.getId(), false));

        assertEquals("REASON_CATALOG_MINIMUM_REQUIRED", exception.getCode());
        verify(repository, never()).save(any(ReasonCatalog.class));
    }

    @Test
    void listsActiveReasonsInConfiguredType() {
        ReasonCatalog reason = ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION,
                "MEDICAL_EMERGENCY", "Có việc đột xuất");
        when(repository.findByTypeAndActiveTrueOrderByLabelAsc(ReasonCatalogType.APPOINTMENT_CANCELLATION))
                .thenReturn(List.of(reason));

        assertEquals(List.of("Có việc đột xuất"), service.list(ReasonCatalogType.APPOINTMENT_CANCELLATION, true)
                .stream().map(ReasonCatalogResponse::label).toList());
    }

    private static void setId(ReasonCatalog reason, UUID id) {
        try {
            var field = ReasonCatalog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(reason, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

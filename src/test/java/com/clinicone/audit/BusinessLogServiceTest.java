package com.clinicone.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessLogServiceTest {
    private final BusinessLogRepository repository = mock(BusinessLogRepository.class);
    private final BusinessLogService service = new BusinessLogService(repository);

    @Test
    void doesNotWriteWhenStateDidNotChange() {
        service.recordTransition(UUID.randomUUID(), "APPOINTMENT", UUID.randomUUID(),
                "BOOKED", "BOOKED", "RETRY", "patient", null);

        verifyNoInteractions(repository);
    }

    @Test
    void writesTransitionAndUsesSystemActorWhenActorIsMissing() {
        UUID eventId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.existsByEventIdAndEntityTypeAndEntityId(eventId, "APPOINTMENT", entityId)).thenReturn(false);

        service.recordTransition(eventId, "APPOINTMENT", entityId, "BOOKED", "CANCELLED",
                "CANCEL_APPOINTMENT", null, "  patient requested  ");

        verify(repository).save(argThat(log -> log.getActor().equals("SYSTEM")
                && log.getReason().equals("patient requested")
                && log.getPreviousStatus().equals("BOOKED")
                && log.getNextStatus().equals("CANCELLED")));
    }

    @Test
    void rejectsMissingTransitionData() {
        assertThatThrownBy(() -> service.recordTransition(null, "APPOINTMENT", UUID.randomUUID(),
                "BOOKED", "CANCELLED", "CANCEL", "staff", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPageLargerThanHundred() {
        assertThatThrownBy(() -> service.page("APPOINTMENT", UUID.randomUUID(), 0, 101))
                .hasMessageContaining("1 đến 100");
        verifyNoInteractions(repository);
    }
}

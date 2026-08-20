package com.clinicone.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class BusinessLogServiceTest {
    private final BusinessLogRepository repository = mock(BusinessLogRepository.class);
    private final BusinessLogService service = BusinessLogService.builder()
            .repository(repository)
            .build();

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
    void writesOperationalActivityEvenWhenLifecycleStatusStaysTheSame() {
        UUID eventId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.existsByEventIdAndEntityTypeAndEntityId(eventId, "QUEUE_TICKET", entityId)).thenReturn(false);

        service.recordActivity(eventId, "QUEUE_TICKET", entityId, "WAITING", "WAITING",
                "QUEUE_PRIORITY_CHANGED", "reception-1", "Ưu tiên theo chỉ định vận hành");

        verify(repository).save(argThat(log -> log.getPreviousStatus().equals("WAITING")
                && log.getNextStatus().equals("WAITING")
                && log.getEventType().equals("QUEUE_PRIORITY_CHANGED")));
    }

    @Test
    void sealsEveryNewLogWithSha256Hash() {
        UUID eventId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.existsByEventIdAndEntityTypeAndEntityId(eventId, "APPOINTMENT", entityId)).thenReturn(false);

        service.recordActivity(eventId, "APPOINTMENT", entityId, "BOOKED", "CHECKED_IN",
                "CHECK_IN", "STAFF", "Đã xác nhận tiếp nhận");

        verify(repository).save(argThat(log -> log.getPreviousHash() == null
                && log.getHash() != null && log.getHash().matches("[0-9a-f]{64}")));
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

    @Test
    void searchRecentLogsWithoutIdentifierQueriesAllDescending() {
        PageRequest pageable = PageRequest.of(0, 50);
        when(repository.findAllByOrderByOccurredAtDescIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of()));

        BusinessLogPageResponse response = service.search(null, null, 0, 50);

        assertThat(response.items()).isEmpty();
        verify(repository).findAllByOrderByOccurredAtDescIdDesc(pageable);
    }

    @Test
    void searchWithEntityTypeOnlyQueriesByEntityTypeDescending() {
        PageRequest pageable = PageRequest.of(0, 50);
        when(repository.findByEntityTypeOrderByOccurredAtDescIdDesc("APPOINTMENT", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        BusinessLogPageResponse response = service.search("APPOINTMENT", null, 0, 50);

        assertThat(response.items()).isEmpty();
        verify(repository).findByEntityTypeOrderByOccurredAtDescIdDesc("APPOINTMENT", pageable);
    }

    @Test
    void searchWithUUIDQueriesByEntityId() {
        UUID entityId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 50);
        when(repository.findByEntityIdOrderByOccurredAtDescIdDesc(entityId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        BusinessLogPageResponse response = service.search(null, entityId.toString(), 0, 50);

        assertThat(response.items()).isEmpty();
        verify(repository).findByEntityIdOrderByOccurredAtDescIdDesc(entityId, pageable);
    }

    @Test
    void searchWithAppointmentCodeResolvesAppointmentUUID() {
        AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
        BusinessLogService searchService = BusinessLogService.builder()
                .repository(repository)
                .appointmentRepository(appointmentRepository)
                .build();

        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(appointmentId);
        when(appointmentRepository.findByAppointmentCode("CL-20260820-TQ01"))
                .thenReturn(Optional.of(appointment));

        PageRequest pageable = PageRequest.of(0, 50);
        when(repository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("APPOINTMENT", appointmentId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        BusinessLogPageResponse response = searchService.search(null, "CL-20260820-TQ01", 0, 50);

        assertThat(response.items()).isEmpty();
        verify(repository).findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("APPOINTMENT", appointmentId, pageable);
    }

    @Test
    void searchWithUnknownCodeReturnsEmptyPageWithoutThrowing() {
        AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
        BusinessLogService searchService = BusinessLogService.builder()
                .repository(repository)
                .appointmentRepository(appointmentRepository)
                .build();

        when(appointmentRepository.findByAppointmentCode("CL-UNKNOWN"))
                .thenReturn(Optional.empty());

        BusinessLogPageResponse response = searchService.search(null, "CL-UNKNOWN", 0, 50);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
        verify(repository, never()).findByEntityIdOrderByOccurredAtDescIdDesc(any(), any());
    }
}

package com.clinicone.reconciliation;

import com.clinicone.audit.BusinessLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {
    private final ReconciliationIncidentRepository repository = mock(ReconciliationIncidentRepository.class);
    private final BusinessLogRepository businessLogRepository = mock(BusinessLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T07:00:00Z"), ZoneOffset.UTC);
    private final ReconciliationService service = new ReconciliationService(repository, businessLogRepository, clock);

    @Test
    void closesOnlyAfterExplicitActionAndReference() {
        UUID id = UUID.randomUUID();
        ReconciliationIncident incident = ReconciliationIncident.open("INC-TEST", "APPOINTMENT", id,
                UUID.randomUUID(), "Thiếu nhật ký chuyển trạng thái", "coordinator");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(incident));
        when(repository.save(any(ReconciliationIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationResponse response = service.close(id,
                new CloseReconciliationRequest(ReconciliationAction.NO_ACTION_REQUIRED,
                        ReconciliationReferenceType.INCIDENT, "INC-TEST",
                        "Đã chạy lại luồng và kiểm tra dữ liệu."), "coordinator-1");

        assertThat(response.status()).isEqualTo(ReconciliationStatus.CLOSED);
        assertThat(response.closedBy()).isEqualTo("coordinator-1");
        assertThat(response.resolutionAction()).isEqualTo(ReconciliationAction.NO_ACTION_REQUIRED);
    }

    @Test
    void rejectsClosingWithoutValidBusinessLogReference() {
        UUID id = UUID.randomUUID();
        ReconciliationIncident incident = ReconciliationIncident.open("INC-TEST", "APPOINTMENT", id,
                null, "Thiếu nhật ký chuyển trạng thái", "coordinator");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(incident));
        when(businessLogRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(id,
                new CloseReconciliationRequest(ReconciliationAction.REPLAY_LOG,
                        ReconciliationReferenceType.BUSINESS_LOG, UUID.randomUUID().toString(),
                        "Đã kiểm tra lại nhật ký."), "coordinator-1"))
                .hasMessageContaining("Không tìm thấy nhật ký");
    }

    @Test
    void opensIncidentAsOpen() {
        when(repository.save(any(ReconciliationIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationResponse response = service.open(new OpenReconciliationRequest(
                "QUEUE_TICKET", UUID.randomUUID(), null, "Dữ liệu hàng đợi không đầy đủ", "coordinator"));

        assertThat(response.status()).isEqualTo(ReconciliationStatus.OPEN);
        assertThat(response.incidentCode()).startsWith("INC-");
    }
}

package com.clinicone.audit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessAuditServiceTest {
    private final AccessAuditRepository repository = mock(AccessAuditRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T07:00:00Z"), ZoneOffset.UTC);
    private final AccessAuditService service = new AccessAuditService(repository, clock);

    @Test
    void recordsLoginWithoutSensitiveValues() {
        when(repository.save(any(AccessAuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessAuditResponse response = service.record("STAFF_LOGIN", "admin", "SUCCESS", "/api/v1/staff/auth/login",
                "127.0.0.1");

        assertThat(response.eventType()).isEqualTo("STAFF_LOGIN");
        assertThat(response.outcome()).isEqualTo("SUCCESS");
        assertThat(response.occurredAt()).isEqualTo(Instant.parse("2026-08-10T07:00:00Z"));
        assertThat(response.toString()).doesNotContain("password");
    }

    @Test
    void listsFilteredEventsReadOnly() {
        when(repository.findFiltered(Instant.parse("1900-01-01T00:00:00Z"),
                Instant.parse("9999-12-31T23:59:59.999999Z"), null, null, null)).thenReturn(List.of());
        assertThat(service.list(null, null, null, null, null)).isEmpty();
    }
}

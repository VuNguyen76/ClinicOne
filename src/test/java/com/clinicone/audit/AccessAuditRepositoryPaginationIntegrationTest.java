package com.clinicone.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccessAuditRepositoryPaginationIntegrationTest {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    @Autowired
    private AccessAuditRepository repository;

    @Test
    void nativeAuditQueryAppliesPageLimitInDatabase() {
        String actor = "pagination-test-" + UUID.randomUUID();
        repository.saveAllAndFlush(IntStream.range(0, 501)
                .mapToObj(index -> AccessAuditEvent.record("TEST", actor, "SUCCESS", "/test", null,
                        FROM.plusSeconds(index)))
                .toList());

        var result = repository.findFiltered(FROM, TO, actor, "", "", PageRequest.of(0, 500));

        assertThat(result).hasSize(500);
        assertThat(result.get(0).getOccurredAt()).isEqualTo(FROM.plusSeconds(500));
        assertThat(result.get(result.size() - 1).getOccurredAt()).isEqualTo(FROM.plusSeconds(1));
    }
}

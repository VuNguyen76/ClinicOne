package com.clinicone.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-08-10T07:00:00Z");

    @Test
    void retriesTwiceThenFailsOnThirdAttempt() {
        SmsDelivery delivery = SmsDelivery.pending(UUID.randomUUID(), "event-1", "0900000001",
                "ClinicOne: Lịch hẹn đã được ghi nhận.", NOW);

        delivery.claim(NOW, NOW.plusSeconds(300));
        delivery.markFailed(NOW, "provider unavailable");
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.RETRY_WAITING);
        assertThat(delivery.getAvailableAt()).isEqualTo(NOW.plusSeconds(300));

        Instant retry = NOW.plusSeconds(300);
        delivery.claim(retry, retry.plusSeconds(300));
        delivery.markFailed(retry, "provider unavailable");
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.RETRY_WAITING);

        Instant lastRetry = retry.plusSeconds(300);
        delivery.claim(lastRetry, lastRetry.plusSeconds(300));
        delivery.markFailed(lastRetry, "provider unavailable");
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(3);
    }

    @Test
    void sentDeliveryCannotBeClaimedAgain() {
        SmsDelivery delivery = SmsDelivery.pending(UUID.randomUUID(), "event-2", "0900000001",
                "ClinicOne: Lịch hẹn đã được ghi nhận.", NOW);
        delivery.claim(NOW, NOW.plusSeconds(300));
        delivery.markSent(NOW.plus(1, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> delivery.claim(NOW.plusSeconds(600), NOW.plusSeconds(900)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void manualRetryResetsFailedDeliveryAndIsIdempotent() {
        SmsDelivery delivery = SmsDelivery.pending(UUID.randomUUID(), "event-retry", "0900000001",
                "ClinicOne: thử lại.", NOW);
        delivery.claim(NOW, NOW.plusSeconds(300));
        delivery.markFailed(NOW, "provider unavailable");
        delivery.claim(NOW.plusSeconds(300), NOW.plusSeconds(600));
        delivery.markFailed(NOW.plusSeconds(300), "provider unavailable");
        delivery.claim(NOW.plusSeconds(600), NOW.plusSeconds(900));
        delivery.markFailed(NOW.plusSeconds(600), "provider unavailable");

        assertThat(delivery.manualRetry(NOW.plusSeconds(601), "retry-1")).isTrue();
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.manualRetry(NOW.plusSeconds(602), "retry-1")).isFalse();
        assertThat(delivery.getAttempts()).isZero();
    }

    @Test
    void doesNotReclaimExpiredLeaseAfterTheThirdAttempt() {
        SmsDelivery delivery = SmsDelivery.pending(UUID.randomUUID(), "event-3", "0900000001",
                "ClinicOne: Lịch hẹn đã được ghi nhận.", NOW);
        delivery.claim(NOW, NOW.plusSeconds(300));
        delivery.markFailed(NOW, "provider unavailable");
        Instant retry = NOW.plusSeconds(300);
        delivery.claim(retry, retry.plusSeconds(300));
        delivery.markFailed(retry, "provider unavailable");
        Instant lastAttempt = retry.plusSeconds(300);
        delivery.claim(lastAttempt, lastAttempt.plusSeconds(300));

        SmsDeliveryRepository repository = mock(SmsDeliveryRepository.class);
        SmsSender sender = mock(SmsSender.class);
        ObjectProvider<SmsSender> senders = mock(ObjectProvider.class);
        when(senders.getIfAvailable()).thenReturn(sender);
        UUID deliveryId = UUID.randomUUID();
        when(repository.findByIdForUpdate(deliveryId)).thenReturn(Optional.of(delivery));
        SmsDeliveryService service = new SmsDeliveryService(repository, senders, new SmsContentPolicy(),
                Clock.fixed(lastAttempt.plusSeconds(301), ZoneOffset.UTC));

        boolean claimed = service.claim(deliveryId, lastAttempt.plusSeconds(301));

        assertThat(claimed).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(3);
        verify(repository).save(delivery);
    }

    @Test
    void deliveryStateChangesHaveTheirOwnTransactionBoundary() throws Exception {
        assertThat(SmsDeliveryStateService.class.getMethod("claim", UUID.class, Instant.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(SmsDeliveryStateService.class.getMethod("markSent", UUID.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(SmsDeliveryStateService.class.getMethod("markFailed", UUID.class, RuntimeException.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void metricsExposeDeliveryCountersWithStableNames() {
        SmsDeliveryMetrics metrics = new SmsDeliveryMetrics(new SimpleMeterRegistry());

        metrics.enqueued();
        metrics.claimed(2);
        metrics.sent();
        metrics.failed();
        metrics.retry();
        metrics.backlog(4);
        io.micrometer.core.instrument.Timer.Sample sample = metrics.workerStarted();
        metrics.workerFinished(sample);

        assertThat(metrics.registry().counter("clinicone.sms.delivery.enqueued").count()).isEqualTo(1);
        assertThat(metrics.registry().counter("clinicone.sms.delivery.claimed").count()).isEqualTo(2);
        assertThat(metrics.registry().counter("clinicone.sms.delivery.sent").count()).isEqualTo(1);
        assertThat(metrics.registry().counter("clinicone.sms.delivery.failed").count()).isEqualTo(1);
        assertThat(metrics.registry().counter("clinicone.sms.delivery.retry").count()).isEqualTo(1);
        assertThat(metrics.registry().find("clinicone.sms.delivery.backlog").gauge().value()).isEqualTo(4);
        assertThat(metrics.registry().timer("clinicone.sms.worker.duration").count()).isEqualTo(1);
    }
}

package com.clinicone.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        when(repository.findByIdForUpdate(deliveryId)).thenReturn(java.util.Optional.of(delivery));
        SmsDeliveryService service = new SmsDeliveryService(repository, senders, new SmsContentPolicy(),
                Clock.fixed(lastAttempt.plusSeconds(301), java.time.ZoneOffset.UTC));

        boolean claimed = service.claim(deliveryId, lastAttempt.plusSeconds(301));

        assertThat(claimed).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(3);
        verify(repository).save(delivery);
    }
}

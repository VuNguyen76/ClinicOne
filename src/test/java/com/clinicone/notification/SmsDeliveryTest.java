package com.clinicone.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
}

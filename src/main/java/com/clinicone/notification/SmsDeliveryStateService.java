package com.clinicone.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Owns the short database transactions used by the SMS worker. Keeping these
 * operations on a separate Spring bean ensures calls from the worker go
 * through the transaction proxy instead of becoming self-invocations.
 */
@Service
public class SmsDeliveryStateService {
    private static final long CLAIM_SECONDS = 5 * 60L;

    private final SmsDeliveryRepository repository;
    private final Clock clock;

    public SmsDeliveryStateService(SmsDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean claim(UUID deliveryId, Instant current) {
        SmsDelivery delivery = repository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null) return false;
        boolean staleProcessing = delivery.getStatus() == SmsDeliveryStatus.PROCESSING
                && delivery.getLockedUntil() != null && !delivery.getLockedUntil().isAfter(current);
        boolean ready = delivery.getStatus() == SmsDeliveryStatus.PENDING
                || delivery.getStatus() == SmsDeliveryStatus.RETRY_WAITING;
        if (!ready && !staleProcessing) return false;
        if (ready && delivery.getAvailableAt().isAfter(current)) return false;
        if (staleProcessing && delivery.getAttempts() >= SmsDelivery.MAX_ATTEMPTS) {
            delivery.markFailed(current, "SMS delivery lease expired after final attempt");
            repository.save(delivery);
            return false;
        }
        delivery.claim(current, current.plusSeconds(CLAIM_SECONDS));
        repository.save(delivery);
        return true;
    }

    @Transactional
    public void markSent(UUID deliveryId) {
        repository.findByIdForUpdate(deliveryId).ifPresent(delivery -> {
            if (delivery.getStatus() == SmsDeliveryStatus.PROCESSING) {
                delivery.markSent(now());
                repository.save(delivery);
            }
        });
    }

    @Transactional
    public void markFailed(UUID deliveryId, RuntimeException failure) {
        repository.findByIdForUpdate(deliveryId).ifPresent(delivery -> {
            if (delivery.getStatus() == SmsDeliveryStatus.PROCESSING) {
                delivery.markFailed(now(), failure.getMessage());
                repository.save(delivery);
            }
        });
    }

    private Instant now() {
        return Instant.now(clock);
    }
}

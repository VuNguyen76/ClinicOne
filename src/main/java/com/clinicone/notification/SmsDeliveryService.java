package com.clinicone.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SmsDeliveryService {
    private static final int BATCH_SIZE = 50;
    private static final long CLAIM_SECONDS = 5 * 60L;

    private final SmsDeliveryRepository repository;
    private final SmsSender smsSender;
    private final SmsContentPolicy contentPolicy;
    private final Clock clock;

    public SmsDeliveryService(SmsDeliveryRepository repository, ObjectProvider<SmsSender> smsSenders,
                              SmsContentPolicy contentPolicy, Clock clock) {
        this.repository = repository;
        this.smsSender = smsSenders.getIfAvailable();
        this.contentPolicy = contentPolicy;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(UUID patientAccountId, String eventKey, String phone, String message) {
        if (patientAccountId == null || eventKey == null || eventKey.isBlank() || phone == null || phone.isBlank()) {
            return;
        }
        contentPolicy.validate(message);
        if (repository.findByEventKey(eventKey).isEmpty()) {
            repository.save(SmsDelivery.pending(patientAccountId, eventKey, phone.trim(), message.trim(), now()));
        }
    }

    public int processDue() {
        if (smsSender == null) return 0;
        Instant current = now();
        List<SmsDelivery> due = repository.findDue(current, PageRequest.of(0, BATCH_SIZE));
        int processed = 0;
        for (SmsDelivery candidate : due) {
            if (claim(candidate.getId(), current)) {
                processed++;
                deliver(candidate.getId());
            }
        }
        return processed;
    }

    @Transactional(readOnly = true)
    public List<SmsDelivery> listRecent() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional
    boolean claim(UUID deliveryId, Instant current) {
        SmsDelivery delivery = repository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null) return false;
        boolean staleProcessing = delivery.getStatus() == SmsDeliveryStatus.PROCESSING
                && delivery.getLockedUntil() != null && !delivery.getLockedUntil().isAfter(current);
        boolean ready = delivery.getStatus() == SmsDeliveryStatus.PENDING
                || delivery.getStatus() == SmsDeliveryStatus.RETRY_WAITING;
        if (!ready && !staleProcessing) return false;
        if (ready && delivery.getAvailableAt().isAfter(current)) return false;
        delivery.claim(current, current.plusSeconds(CLAIM_SECONDS));
        repository.save(delivery);
        return true;
    }

    @Transactional
    void markSent(UUID deliveryId) {
        repository.findByIdForUpdate(deliveryId).ifPresent(delivery -> {
            if (delivery.getStatus() == SmsDeliveryStatus.PROCESSING) {
                delivery.markSent(now());
                repository.save(delivery);
            }
        });
    }

    @Transactional
    void markFailed(UUID deliveryId, RuntimeException failure) {
        repository.findByIdForUpdate(deliveryId).ifPresent(delivery -> {
            if (delivery.getStatus() == SmsDeliveryStatus.PROCESSING) {
                delivery.markFailed(now(), failure.getMessage());
                repository.save(delivery);
            }
        });
    }

    private void deliver(UUID deliveryId) {
        SmsDelivery delivery = repository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != SmsDeliveryStatus.PROCESSING) return;
        try {
            smsSender.sendText(delivery.getPhone(), delivery.getMessage());
            markSent(deliveryId);
        } catch (RuntimeException failure) {
            markFailed(deliveryId, failure);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}

package com.clinicone.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final SmsDeliveryRepository repository;
    private final SmsSender smsSender;
    private final SmsContentPolicy contentPolicy;
    private final Clock clock;
    private final SmsDeliveryStateService stateService;

    public SmsDeliveryService(SmsDeliveryRepository repository, ObjectProvider<SmsSender> smsSenders,
                              SmsContentPolicy contentPolicy, Clock clock) {
        this(repository, smsSenders, contentPolicy, clock, new SmsDeliveryStateService(repository, clock));
    }

    @Autowired
    public SmsDeliveryService(SmsDeliveryRepository repository, ObjectProvider<SmsSender> smsSenders,
                              SmsContentPolicy contentPolicy, Clock clock,
                              SmsDeliveryStateService stateService) {
        this.repository = repository;
        this.smsSender = smsSenders.getIfAvailable();
        this.contentPolicy = contentPolicy;
        this.clock = clock;
        this.stateService = stateService;
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
        return stateService.claim(deliveryId, current);
    }

    void markSent(UUID deliveryId) {
        stateService.markSent(deliveryId);
    }

    void markFailed(UUID deliveryId, RuntimeException failure) {
        stateService.markFailed(deliveryId, failure);
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

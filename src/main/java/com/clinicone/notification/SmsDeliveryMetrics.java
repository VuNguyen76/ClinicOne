package com.clinicone.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The small metric surface used by the SMS outbox. Keeping names in one place
 * prevents dashboards and alerts from depending on implementation details.
 */
@Component
public final class SmsDeliveryMetrics {
    private final MeterRegistry registry;
    private final Counter enqueuedCounter;
    private final Counter claimedCounter;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public SmsDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.enqueuedCounter = counter("clinicone.sms.delivery.enqueued");
        this.claimedCounter = counter("clinicone.sms.delivery.claimed");
        this.sentCounter = counter("clinicone.sms.delivery.sent");
        this.failedCounter = counter("clinicone.sms.delivery.failed");
    }

    public void enqueued() {
        enqueuedCounter.increment();
    }

    public void claimed(int count) {
        if (count > 0) claimedCounter.increment(count);
    }

    public void sent() {
        sentCounter.increment();
    }

    public void failed() {
        failedCounter.increment();
    }

    MeterRegistry registry() {
        return registry;
    }

    private Counter counter(String name) {
        return registry == null ? Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder(name).register(registry);
    }
}

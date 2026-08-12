package com.clinicone.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

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
    private final Counter retryCounter;
    private final AtomicLong backlogValue = new AtomicLong();
    private final Timer workerDuration;

    public SmsDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.enqueuedCounter = counter("clinicone.sms.delivery.enqueued");
        this.claimedCounter = counter("clinicone.sms.delivery.claimed");
        this.sentCounter = counter("clinicone.sms.delivery.sent");
        this.failedCounter = counter("clinicone.sms.delivery.failed");
        this.retryCounter = counter("clinicone.sms.delivery.retry");
        if (registry != null) {
            Gauge.builder("clinicone.sms.delivery.backlog", backlogValue, AtomicLong::doubleValue)
                    .register(registry);
            this.workerDuration = Timer.builder("clinicone.sms.worker.duration").register(registry);
        } else {
            this.workerDuration = Timer.builder("clinicone.sms.worker.duration")
                    .register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
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

    public void retry() {
        retryCounter.increment();
    }

    public void backlog(long count) {
        backlogValue.set(Math.max(0, count));
    }

    public Timer.Sample workerStarted() {
        return registry == null ? null : Timer.start(registry);
    }

    public void workerFinished(Timer.Sample sample) {
        if (sample != null) sample.stop(workerDuration);
    }

    MeterRegistry registry() {
        return registry;
    }

    private Counter counter(String name) {
        return registry == null ? Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder(name).register(registry);
    }
}

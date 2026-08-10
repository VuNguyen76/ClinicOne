package com.clinicone.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SmsDeliveryWorker {
    private final SmsDeliveryService service;

    public SmsDeliveryWorker(SmsDeliveryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.notifications.worker-delay-ms:60000}")
    public void processDue() {
        service.processDue();
    }
}

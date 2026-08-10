package com.clinicone.notification;

import java.time.Instant;
import java.util.UUID;

public record SmsDeliveryResponse(UUID id, String eventKey, String phone, SmsDeliveryStatus status,
                                  int attempts, Instant availableAt, Instant sentAt, String lastError,
                                  Instant createdAt) {
    static SmsDeliveryResponse from(SmsDelivery delivery) {
        return new SmsDeliveryResponse(delivery.getId(), delivery.getEventKey(), delivery.getPhone(),
                delivery.getStatus(), delivery.getAttempts(), delivery.getAvailableAt(), delivery.getSentAt(),
                delivery.getLastError(), delivery.getCreatedAt());
    }
}

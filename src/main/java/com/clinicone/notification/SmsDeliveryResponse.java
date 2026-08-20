package com.clinicone.notification;

import java.time.Instant;
import java.util.UUID;

public record SmsDeliveryResponse(UUID id, String eventKey, String phone, SmsDeliveryStatus status,
                                  int attempts, Instant availableAt, Instant sentAt, String lastError,
                                  Instant createdAt, String message) {
    public SmsDeliveryResponse(UUID id, String eventKey, String phone, SmsDeliveryStatus status,
                              int attempts, Instant availableAt, Instant sentAt, String lastError,
                              Instant createdAt) {
        this(id, eventKey, phone, status, attempts, availableAt, sentAt, lastError, createdAt, null);
    }

    public static SmsDeliveryResponse from(SmsDelivery delivery) {
        return new SmsDeliveryResponse(delivery.getId(), delivery.getEventKey(), delivery.getPhone(),
                delivery.getStatus(), delivery.getAttempts(), delivery.getAvailableAt(), delivery.getSentAt(),
                delivery.getLastError(), delivery.getCreatedAt(), delivery.getMessage());
    }
}

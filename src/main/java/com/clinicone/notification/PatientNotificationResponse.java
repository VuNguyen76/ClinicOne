package com.clinicone.notification;

import java.time.Instant;
import java.util.UUID;

public record PatientNotificationResponse(
        UUID id,
        String type,
        String title,
        String message,
        String targetUrl,
        boolean read,
        Instant createdAt
) {
    public static PatientNotificationResponse from(PatientNotification notification) {
        return new PatientNotificationResponse(notification.getId(), notification.getType().name(),
                notification.getTitle(), notification.getMessage(), notification.getTargetUrl(),
                notification.getReadAt() != null, notification.getCreatedAt());
    }
}

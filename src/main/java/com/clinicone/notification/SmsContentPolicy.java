package com.clinicone.notification;

import org.springframework.stereotype.Component;

import java.util.Locale;

/** Rejects payloads that would expose secrets or clinical content over SMS. */
@Component
public class SmsContentPolicy {
    public void validate(String message) {
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new IllegalArgumentException("SMS content is invalid");
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        String[] forbidden = {"otp", "password", "mật khẩu", "diagnos", "prescription", "đơn thuốc",
                "chẩn đoán", "toàn văn", "medical record"};
        for (String token : forbidden) {
            if (normalized.contains(token)) {
                throw new IllegalArgumentException("SMS content contains restricted information");
            }
        }
    }
}

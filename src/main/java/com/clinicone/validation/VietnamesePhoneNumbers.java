package com.clinicone.validation;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;

/** Canonical phone transformations used at service boundaries and by SMS delivery. */
public final class VietnamesePhoneNumbers {
    private static final String LOCAL_PATTERN = "0\\d{9}";
    private static final String SMS_PATTERN = "\\+84\\d{9}";

    private VietnamesePhoneNumbers() {
    }

    public static String local(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!normalized.matches(LOCAL_PATTERN)) {
            throw invalidPhone();
        }
        return normalized;
    }

    public static String smsDestination(String phone) {
        String compact = phone == null ? "" : phone.trim().replaceAll("[\\s().-]", "");
        if (compact.matches(LOCAL_PATTERN)) {
            return "+84" + compact.substring(1);
        }
        if (compact.matches(SMS_PATTERN)) {
            return compact;
        }
        throw invalidPhone();
    }

    private static AuthException invalidPhone() {
        return new AuthException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
    }
}

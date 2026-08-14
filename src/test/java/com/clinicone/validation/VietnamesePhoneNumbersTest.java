package com.clinicone.validation;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VietnamesePhoneNumbersTest {
    @Test
    void normalizesLocalPhoneForAccountLookup() {
        assertEquals("0912345678", VietnamesePhoneNumbers.local(" 0912345678 "));
    }

    @Test
    void convertsFormattedLocalPhoneToSmsDestination() {
        assertEquals("+84912345678", VietnamesePhoneNumbers.smsDestination("0912 345 678"));
        assertEquals("+84912345678", VietnamesePhoneNumbers.smsDestination("+84912345678"));
    }

    @Test
    void rejectsInvalidPhoneAtServiceBoundary() {
        AuthException missing = assertThrows(AuthException.class, () -> VietnamesePhoneNumbers.local(null));
        AuthException invalid = assertThrows(AuthException.class, () -> VietnamesePhoneNumbers.local("112233445"));
        AuthException invalidSms = assertThrows(AuthException.class,
                () -> VietnamesePhoneNumbers.smsDestination("not-a-phone"));

        assertEquals("PHONE_INVALID", missing.getCode());
        assertEquals("PHONE_INVALID", invalid.getCode());
        assertEquals("PHONE_INVALID", invalidSms.getCode());
    }
}

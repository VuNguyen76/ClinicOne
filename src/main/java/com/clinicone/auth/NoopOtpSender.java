package com.clinicone.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used by tests and local profiles that do not configure an OTP provider. */
@Component
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopOtpSender implements OtpSender {

    @Override
    public void send(String destination, OtpPurpose purpose, String code) {
        // Deliberately do nothing. Production uses TextBee with OTP_PROVIDER=textbee.
    }
}

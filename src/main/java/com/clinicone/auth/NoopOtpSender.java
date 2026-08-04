package com.clinicone.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used by tests and local profiles that do not configure an email provider. */
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host", matchIfMissing = true)
public class NoopOtpSender implements OtpSender {

    @Override
    public void send(String email, OtpPurpose purpose, String code) {
        // Deliberately do nothing. Production requires spring.mail.host and uses GmailOtpSender.
    }
}

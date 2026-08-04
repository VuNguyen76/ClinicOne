package com.clinicone.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureSessionTokenGenerator implements SessionTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}

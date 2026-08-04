package com.clinicone.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureOtpCodeGenerator implements OtpCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}

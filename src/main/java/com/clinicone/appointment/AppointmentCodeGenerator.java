package com.clinicone.appointment;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates opaque appointment lookup codes without patient or date data. */
@Component
public class AppointmentCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RANDOM_PART_LENGTH = 12;

    private final SecureRandom random;

    public AppointmentCodeGenerator() {
        this(new SecureRandom());
    }

    AppointmentCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String nextCode() {
        StringBuilder code = new StringBuilder("CL-");
        for (int index = 0; index < RANDOM_PART_LENGTH; index++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}

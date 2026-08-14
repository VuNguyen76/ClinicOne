package com.clinicone.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VietnamesePhoneTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsCanonicalTenDigitVietnamesePhone() {
        assertTrue(validator.validate(new PhoneRequest("0912345678")).isEmpty());
    }

    @Test
    void rejectsNonCanonicalPhone() {
        assertFalse(validator.validate(new PhoneRequest("+84912345678")).isEmpty());
        assertFalse(validator.validate(new PhoneRequest("091234567")).isEmpty());
    }

    private record PhoneRequest(@NotBlank @VietnamesePhone String phone) {
    }
}

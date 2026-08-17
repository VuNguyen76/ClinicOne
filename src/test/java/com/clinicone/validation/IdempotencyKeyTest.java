package com.clinicone.validation;

import com.clinicone.auth.AuthException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyKeyTest {
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
    void acceptsMissingOptionalKeyAndKeysUpToEightyCharacters() {
        assertTrue(validator.validate(new OptionalRequest(null)).isEmpty());
        assertTrue(validator.validate(new OptionalRequest("a".repeat(80))).isEmpty());
    }

    @Test
    void rejectsKeysLongerThanEightyCharacters() {
        assertFalse(validator.validate(new OptionalRequest("a".repeat(81))).isEmpty());
    }

    @Test
    void normalizesOptionalAndRequiredKeys() {
        assertNull(IdempotencyKeys.optional("  "));
        assertEquals("request-1", IdempotencyKeys.optional(" request-1 "));
        assertEquals("request-2", IdempotencyKeys.required(" request-2 ", "required"));
    }

    @Test
    void rejectsMissingOrOversizedKeysAtTheServiceBoundary() {
        AuthException missing = assertThrows(AuthException.class,
                () -> IdempotencyKeys.required(" ", "Cần khóa chống trùng."));
        AuthException oversized = assertThrows(AuthException.class,
                () -> IdempotencyKeys.optional("a".repeat(81)));

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", missing.getCode());
        assertEquals("IDEMPOTENCY_KEY_INVALID", oversized.getCode());
    }

    private record OptionalRequest(@IdempotencyKey String requestKey) {
    }
}

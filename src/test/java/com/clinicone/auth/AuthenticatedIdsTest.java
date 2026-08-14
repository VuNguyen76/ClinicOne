package com.clinicone.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedIdsTest {
    @Test
    void parsesValidAuthenticatedIds() {
        UUID id = UUID.randomUUID();

        assertEquals(id, AuthenticatedIds.patient(id.toString()));
        assertEquals(id, AuthenticatedIds.staff(id.toString()));
        assertEquals(id, AuthenticatedIds.doctor(id.toString()));
    }

    @Test
    void rejectsInvalidAuthenticatedIdsWithTheSharedErrorCode() {
        AuthException patient = assertThrows(AuthException.class, () -> AuthenticatedIds.patient("invalid"));
        AuthException staff = assertThrows(AuthException.class, () -> AuthenticatedIds.staff("invalid"));
        AuthException doctor = assertThrows(AuthException.class, () -> AuthenticatedIds.doctor("invalid"));

        assertEquals("AUTHENTICATION_REQUIRED", patient.getCode());
        assertEquals("AUTHENTICATION_REQUIRED", staff.getCode());
        assertEquals("AUTHENTICATION_REQUIRED", doctor.getCode());
    }
}

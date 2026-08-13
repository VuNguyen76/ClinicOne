package com.clinicone.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveEntityAnnotationTest {

    @Test
    void sensitivePersistenceFieldsAreNeverSerializedAsJson() throws Exception {
        assertJsonIgnored(PatientAccount.class, "passwordHash");
        assertJsonIgnored(StaffAccount.class, "passwordHash");
        assertJsonIgnored(OtpChallenge.class, "codeHash");
        assertJsonIgnored(LoginSession.class, "tokenHash");
    }

    @Test
    void bulkUpdateRepositoriesFlushAndClearPersistenceContext() throws Exception {
        assertBulkMutationConfigured(LoginSessionRepository.class, "revokeActiveByAccountId");
        assertBulkMutationConfigured(com.clinicone.schedule.AppointmentHoldRepository.class,
                "deleteActiveByDoctorAndDateRange");
    }

    private void assertJsonIgnored(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        assertTrue(field.isAnnotationPresent(JsonIgnore.class),
                () -> type.getSimpleName() + "." + fieldName + " must be annotated with @JsonIgnore");
    }

    private void assertBulkMutationConfigured(Class<?> type, String methodName) throws NoSuchMethodException {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(type.getName() + "." + methodName));
        Modifying modifying = method.getAnnotation(Modifying.class);
        assertTrue(modifying != null && modifying.clearAutomatically() && modifying.flushAutomatically(),
                () -> type.getSimpleName() + "." + methodName
                        + " must flush and clear the persistence context");
    }
}

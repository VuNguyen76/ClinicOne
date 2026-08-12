package com.clinicone.config;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicConfigurationServiceTest {
    private final ClinicConfigurationRepository repository = mock(ClinicConfigurationRepository.class);
    private final ClinicConfigurationService service = new ClinicConfigurationService(repository);

    @Test
    void returnsPersistedDefaultsWhenConfigurationHasNotBeenCreated() {
        when(repository.findById(ClinicConfiguration.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(ClinicConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicConfigurationResponse response = service.get();

        assertEquals("ClinicOne", response.unitName());
        assertEquals(10, response.holdMinutes());
        assertEquals(12, response.cancellationThresholdHours());
        verify(repository).save(any(ClinicConfiguration.class));
    }

    @Test
    void updatesOperationalConfigurationWithinSrsLimits() {
        ClinicConfiguration configuration = ClinicConfiguration.defaults();
        when(repository.findById(ClinicConfiguration.DEFAULT_ID)).thenReturn(Optional.of(configuration));
        when(repository.save(configuration)).thenReturn(configuration);

        ClinicConfigurationResponse response = service.update(
                new UpdateClinicConfigurationRequest("Bệnh viện ClinicOne", "Khoa khám bệnh", 30, 72), "admin");

        assertEquals("Bệnh viện ClinicOne", response.unitName());
        assertEquals(30, response.holdMinutes());
        assertEquals(72, response.cancellationThresholdHours());
        assertEquals("admin", response.updatedBy());
    }

    @Test
    void rejectsHoldDurationOutsideFiveToThirtyMinutes() {
        AuthException exception = assertThrows(AuthException.class, () -> service.update(
                new UpdateClinicConfigurationRequest("ClinicOne", "Khám bệnh", 31, 12), "admin"));

        assertEquals("HOLD_MINUTES_INVALID", exception.getCode());
    }

    @Test
    void rejectsCancellationThresholdOutsideZeroToSeventyTwoHours() {
        AuthException exception = assertThrows(AuthException.class, () -> service.update(
                new UpdateClinicConfigurationRequest("ClinicOne", "Khám bệnh", 10, 73), "admin"));

        assertEquals("CANCELLATION_THRESHOLD_INVALID", exception.getCode());
    }
}

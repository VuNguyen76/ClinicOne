package com.clinicone.notification;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class PatientNotificationServiceTest {
    private final PatientNotificationRepository repository = mock(PatientNotificationRepository.class);
    private final PatientNotificationService service = new PatientNotificationService(repository);

    @Test
    void createsOnlyOneNotificationForSignedRecordEvent() {
        UUID patientId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        when(repository.existsByEventKey("MEDICAL_RECORD_SIGNED:" + recordId)).thenReturn(false);
        service.notifyMedicalRecordSigned(patientId, recordId, "CL-001", "Bác sĩ An", "Nội khoa");

        verify(repository).save(any(PatientNotification.class));
    }

    @Test
    void doesNotCreateNotificationForMissingRecordId() {
        service.notifyMedicalRecordSigned(UUID.randomUUID(), null, "CL-001", "Bác sĩ An", "Nội khoa");

        verify(repository, never()).existsByEventKey(any());
        verify(repository, never()).save(any(PatientNotification.class));
    }
}

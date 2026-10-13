package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorMedicalRecordServiceTest {
    private static final UUID DOCTOR_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID RECORD_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Test
    void listsOnlyThisDoctorsSignedRecordsAndBoundsSearchAndDates() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        DoctorMedicalRecordService service = new DoctorMedicalRecordService(repository);
        var pageRequest = PageRequest.of(0, 20);
        var record = mock(MedicalRecord.class);
        when(record.getPrescriptionLines()).thenReturn(List.of());
        when(repository.findSignedHistoryForDoctor(eq(DOCTOR_ID), eq("mai"), any(), any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(record), pageRequest, 1));

        DoctorMedicalRecordHistoryPage result = service.listHistory(DOCTOR_ID.toString(),
                new DoctorMedicalRecordHistoryQuery("mai", LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 13), 0, 20));

        assertEquals(1, result.items().size());
        assertEquals(1, result.totalElements());
        verify(repository).findSignedHistoryForDoctor(eq(DOCTOR_ID), eq("mai"),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 13)), eq(pageRequest));
        verify(record, never()).getPrescriptionLines();
    }

    @Test
    void returnsFullRecordOnlyWhenItBelongsToTheAuthenticatedDoctor() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        DoctorMedicalRecordService service = new DoctorMedicalRecordService(repository);
        var record = mock(MedicalRecord.class);
        when(record.getId()).thenReturn(RECORD_ID);
        when(record.getSession()).thenReturn(null);
        when(record.getPrescriptionLines()).thenReturn(List.of());
        when(repository.findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(RECORD_ID, DOCTOR_ID))
                .thenReturn(Optional.of(record));

        DoctorMedicalRecordResponse response = service.get(DOCTOR_ID.toString(), RECORD_ID);

        assertEquals(RECORD_ID, response.id());
        verify(repository).findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(RECORD_ID, DOCTOR_ID);
    }

    @Test
    void rejectsInvalidDateRangeBeforeQueryingHistory() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        DoctorMedicalRecordService service = new DoctorMedicalRecordService(repository);

        AuthException exception = assertThrows(AuthException.class, () -> service.listHistory(DOCTOR_ID.toString(),
                new DoctorMedicalRecordHistoryQuery(null, LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 1), 0, 20)));

        assertEquals(400, exception.getStatus().value());
        assertEquals("DOCTOR_MEDICAL_RECORD_DATE_RANGE_INVALID", exception.getCode());
        verify(repository, never()).findSignedHistoryForDoctor(any(), any(), any(), any(), any());
    }

    @Test
    void returnsNotFoundForRecordsOwnedByAnotherDoctor() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        DoctorMedicalRecordService service = new DoctorMedicalRecordService(repository);
        when(repository.findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(RECORD_ID, DOCTOR_ID))
                .thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> service.get(DOCTOR_ID.toString(), RECORD_ID));

        assertEquals(404, exception.getStatus().value());
        assertEquals("DOCTOR_MEDICAL_RECORD_NOT_FOUND", exception.getCode());
    }
}

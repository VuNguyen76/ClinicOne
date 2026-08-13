package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MedicalRecordServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Test
    void listsOnlySignedRecordsForPatient() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);
        MedicalRecord record = MedicalRecord.signed(null, "BS. Nguyễn An", "Đau đầu", "Khám bình thường",
                "Đau đầu do căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, LocalDate.of(2026, 8, 20));
        when(repository.findSignedHistory(eq(ACCOUNT_ID), eq(null), eq(Instant.parse("1900-01-01T00:00:00Z")),
                eq(Instant.parse("9999-12-31T23:59:59.999999Z")),
                eq(PageRequest.of(0, 20)))).thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));

        MedicalRecordHistoryPage result = service.listHistory(ACCOUNT_ID.toString(),
                new MedicalRecordHistoryQuery(null, null, null, 0, 20));

        assertEquals(1, result.items().size());
        assertEquals("BS. Nguyễn An", result.items().get(0).doctorName());
    }

    @Test
    void doesNotReturnUnsignedRecord() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);
        UUID recordId = UUID.randomUUID();
        when(repository.findByIdAndSession_Appointment_Patient_IdAndSignedAtIsNotNull(recordId, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> service.get(ACCOUNT_ID.toString(), recordId.toString()));

        assertEquals(404, exception.getStatus().value());
        assertEquals("MEDICAL_RECORD_NOT_FOUND", exception.getCode());
    }

    @Test
    void pagesOnlySignedHistoryForTheRequestedProfileAndDateRange() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);
        UUID profileId = UUID.randomUUID();
        MedicalRecord record = MedicalRecord.signed(null, "BS. Nguyễn An", "Đau đầu", "Khám bình thường",
                "Đau đầu do căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, LocalDate.of(2026, 8, 20));
        when(repository.findSignedHistory(eq(ACCOUNT_ID), eq(profileId), any(), any(),
                eq(PageRequest.of(0, 20)))).thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 21));

        MedicalRecordHistoryPage result = service.listHistory(ACCOUNT_ID.toString(),
                new MedicalRecordHistoryQuery(profileId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), 0, 20));

        assertEquals(1, result.items().size());
        assertEquals(21, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    void rejectsAnInvalidOrOverlongHistoryDateRangeBeforeQueryingRecords() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);

        AuthException exception = assertThrows(AuthException.class, () -> service.listHistory(ACCOUNT_ID.toString(),
                new MedicalRecordHistoryQuery(null, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 1), 0, 20)));

        assertEquals(400, exception.getStatus().value());
        assertEquals("MEDICAL_RECORD_DATE_RANGE_INVALID", exception.getCode());
    }
}

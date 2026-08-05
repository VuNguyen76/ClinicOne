package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicalRecordServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    @Test
    void listsOnlySignedRecordsForPatient() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);
        MedicalRecord record = MedicalRecord.signed(null, "BS. Nguyễn An", "Đau đầu", "Khám bình thường",
                "Đau đầu do căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, LocalDate.of(2026, 8, 20));
        when(repository.findBySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(ACCOUNT_ID))
                .thenReturn(List.of(record));

        List<MedicalRecordResponse> result = service.list(ACCOUNT_ID.toString());

        assertEquals(1, result.size());
        assertEquals("BS. Nguyễn An", result.get(0).doctorName());
    }

    @Test
    void doesNotReturnUnsignedRecord() {
        MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
        MedicalRecordService service = new MedicalRecordService(repository);
        UUID recordId = UUID.randomUUID();
        when(repository.findByIdAndSession_Appointment_Patient_IdAndSignedAtIsNotNull(recordId, ACCOUNT_ID))
                .thenReturn(java.util.Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> service.get(ACCOUNT_ID.toString(), recordId.toString()));

        assertEquals(404, exception.getStatus().value());
        assertEquals("MEDICAL_RECORD_NOT_FOUND", exception.getCode());
    }
}

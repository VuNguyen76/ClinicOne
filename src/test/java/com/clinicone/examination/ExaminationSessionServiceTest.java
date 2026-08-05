package com.clinicone.examination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExaminationSessionServiceTest {
    @Test
    void listsExaminationSessionsForPatient() {
        ExaminationSessionRepository repository = mock(ExaminationSessionRepository.class);
        ExaminationSessionService service = new ExaminationSessionService(repository);
        when(repository.findByAppointment_Patient_IdOrderByCreatedAtDesc(UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13")))
                .thenReturn(List.of());

        assertEquals(List.of(), service.list("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"));
    }
}

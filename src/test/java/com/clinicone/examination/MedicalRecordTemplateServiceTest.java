package com.clinicone.examination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MedicalRecordTemplateServiceTest {
    @Test
    void coordinatorCanCreateAndDoctorCanSeeOnlyMatchingActiveTemplates() {
        MedicalRecordTemplateRepository repository = mock(MedicalRecordTemplateRepository.class);
        UUID serviceId = UUID.randomUUID();
        MedicalRecordTemplate template = MedicalRecordTemplate.create("GEN-01", "Khám tổng quát", "Khám tổng quát",
                serviceId, "Mẫu mặc định", "reason|Lý do khám|required", "coordinator-1");
        when(repository.existsByCodeIgnoreCase("GEN-01")).thenReturn(false);
        when(repository.save(any())).thenReturn(template);
        MedicalRecordTemplateService service = new MedicalRecordTemplateService(repository);

        MedicalRecordTemplateResponse created = service.create(new MedicalRecordTemplateRequest("GEN-01", "Khám tổng quát",
                "Khám tổng quát", serviceId, "Mẫu mặc định", "reason|Lý do khám|required"), "coordinator-1");

        assertThat(created.active()).isTrue();
        verify(repository).save(any(MedicalRecordTemplate.class));

        when(repository.findAllByActiveTrueOrderBySpecialtyAscNameAsc()).thenReturn(List.of(template));
        assertThat(service.list(true, "Khám tổng quát", serviceId)).hasSize(1);
        assertThat(service.list(true, "Da liễu", serviceId)).isEmpty();
    }

    @Test
    void deactivatedTemplateIsNotOfferedToDoctors() {
        MedicalRecordTemplateRepository repository = mock(MedicalRecordTemplateRepository.class);
        MedicalRecordTemplate template = MedicalRecordTemplate.create("GEN-01", "Khám tổng quát", "Khám tổng quát",
                null, null, "reason|Lý do khám|required", "coordinator-1");
        template.deactivate();
        when(repository.findAllByActiveTrueOrderBySpecialtyAscNameAsc()).thenReturn(List.of());
        assertThat(new MedicalRecordTemplateService(repository).list(true, null, null)).isEmpty();
    }
}

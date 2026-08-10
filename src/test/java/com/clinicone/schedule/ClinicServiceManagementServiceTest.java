package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicServiceManagementServiceTest {
    @Mock ClinicServiceRepository repository;
    @Mock DoctorProfileRepository doctorProfileRepository;
    @Mock SpecialtyCatalogService specialtyCatalog;

    private ClinicServiceManagementService service;
    private UUID doctorId;
    private DoctorProfile doctor;

    @BeforeEach
    void setUp() {
        service = new ClinicServiceManagementService(repository, doctorProfileRepository, specialtyCatalog);
        doctorId = UUID.randomUUID();
        doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        lenient().when(doctor.getId()).thenReturn(UUID.randomUUID());
        lenient().when(doctor.getSpecialty()).thenReturn("Khám Tổng Quát");
        lenient().when(doctor.getStaffAccount()).thenReturn(staff);
        lenient().when(staff.getId()).thenReturn(doctorId);
        lenient().when(staff.getFullName()).thenReturn("Bác sĩ Nguyễn An");
        lenient().when(repository.save(any(ClinicService.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(repository.existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCase(anyString(), anyString(), anyString()))
                .thenReturn(false);
        lenient().when(specialtyCatalog.require("Khám Tổng Quát"))
                .thenReturn(new SpecialtyResponse("TQ", "Khám Tổng Quát", ""));
    }

    @Test
    void createsServiceWithEligibleDoctorAndCanonicalSpecialty() {
        when(doctorProfileRepository.findAllByStaffAccount_IdInAndActiveTrue(List.of(doctorId)))
                .thenReturn(List.of(doctor));

        ClinicServiceResponse response = service.create(new CreateClinicServiceRequest(
                "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường", 30, List.of(doctorId)));

        assertThat(response.name()).isEqualTo("Khám tổng quát cơ bản");
        assertThat(response.specialty()).isEqualTo("Khám Tổng Quát");
        assertThat(response.durationMinutes()).isEqualTo(30);
        assertThat(response.eligibleDoctors()).singleElement()
                .extracting(EligibleDoctorResponse::fullName).isEqualTo("Bác sĩ Nguyễn An");
        ArgumentCaptor<ClinicService> captor = ArgumentCaptor.forClass(ClinicService.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getVisitType()).isEqualTo("Khám thường");
    }

    @Test
    void rejectsDurationOutsideFiveToOneHundredTwentyMinutes() {
        assertThatThrownBy(() -> service.create(new CreateClinicServiceRequest(
                "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường", 121, List.of(doctorId))))
                .isInstanceOf(AuthException.class)
                .hasMessage("Thời lượng dịch vụ phải từ 5 đến 120 phút.");
        verify(repository, never()).save(any(ClinicService.class));
    }

    @Test
    void rejectsDoctorFromAnotherSpecialty() {
        DoctorProfile mismatchedDoctor = mockDoctor("Khám Da Liễu");
        when(doctorProfileRepository.findAllByStaffAccount_IdInAndActiveTrue(List.of(doctorId)))
                .thenReturn(List.of(mismatchedDoctor));

        assertThatThrownBy(() -> service.create(new CreateClinicServiceRequest(
                "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường", 30, List.of(doctorId))))
                .isInstanceOf(AuthException.class)
                .hasMessage("Có bác sĩ không thuộc chuyên khoa của dịch vụ.");
        verify(repository, never()).save(any(ClinicService.class));
    }

    @Test
    void rejectsDuplicateEligibleDoctorIds() {
        assertThatThrownBy(() -> service.create(new CreateClinicServiceRequest(
                "Khám tổng quát cơ bản", "Khám Tổng Quát", "Khám thường", 30, List.of(doctorId, doctorId))))
                .isInstanceOf(AuthException.class)
                .hasMessage("Danh sách bác sĩ đủ điều kiện không được trùng.");
        verify(repository, never()).save(any(ClinicService.class));
    }

    private DoctorProfile mockDoctor(String specialty) {
        DoctorProfile value = mock(DoctorProfile.class);
        when(value.getSpecialty()).thenReturn(specialty);
        return value;
    }
}

package com.clinicone.doctor;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.schedule.SpecialtyCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorManagementServiceTest {
    @Mock StaffAccountRepository staffRepository;
    @Mock DoctorProfileRepository profileRepository;
    @Mock DoctorScheduleRepository scheduleRepository;
    @Mock ClinicRoomRepository roomRepository;
    @Mock SpecialtyCatalogService specialtyCatalog;
    @Mock PasswordEncoder passwordEncoder;

    private DoctorManagementService service;

    @BeforeEach
    void setUp() {
        service = DoctorManagementService.builder()
                .staffRepository(staffRepository)
                .profileRepository(profileRepository)
                .scheduleRepository(scheduleRepository)
                .roomRepository(roomRepository)
                .specialtyCatalog(specialtyCatalog)
                .passwordEncoder(passwordEncoder)
                .build();
    }

    @Test
    void createsDoctorWithEncodedPasswordAndDoctorRole() {
        when(staffRepository.findByUsernameIgnoreCase("bs.an")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("doctor123")).thenReturn("encoded-password");
        when(staffRepository.save(any(StaffAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAccountResponse response = service.createDoctor(
                new DoctorCreateRequest(" bs.an ", " Bác sĩ Nguyễn An ", "doctor123"));

        ArgumentCaptor<StaffAccount> captor = ArgumentCaptor.forClass(StaffAccount.class);
        verify(staffRepository).save(captor.capture());
        StaffAccount saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bs.an");
        assertThat(saved.getFullName()).isEqualTo("Bác sĩ Nguyễn An");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(StaffRole.DOCTOR);
        assertThat(response.fullName()).isEqualTo("Bác sĩ Nguyễn An");
        assertThat(response.assigned()).isFalse();
    }

    @Test
    void rejectsScheduleWithNonPositiveSlotDuration() {
        java.util.UUID staffId = java.util.UUID.randomUUID();
        var doctor = StaffAccount.create("bs.an", "hash", "Bác sĩ An", StaffRole.DOCTOR);
        var profile = org.mockito.Mockito.mock(DoctorProfile.class);
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(doctor));
        when(profileRepository.findByStaffAccount_Id(staffId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addSchedule(staffId, new DoctorScheduleRequest(
                        java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(8, 0),
                        java.time.LocalTime.of(17, 0), 0)))
                .isInstanceOf(AuthException.class)
                .hasMessage("Thời lượng khung giờ phải lớn hơn 0.");
        org.mockito.Mockito.verify(scheduleRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(DoctorSchedule.class));
    }

    @Test
    void rejectsDuplicateUsernameBeforeSaving() {
        when(staffRepository.findByUsernameIgnoreCase("bs.an")).thenReturn(
                Optional.of(StaffAccount.create("bs.an", "hash", "Bác sĩ cũ", StaffRole.DOCTOR)));

        assertThatThrownBy(() -> service.createDoctor(new DoctorCreateRequest("bs.an", "Bác sĩ mới", "doctor123")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Tên đăng nhập đã được sử dụng.");
    }
}

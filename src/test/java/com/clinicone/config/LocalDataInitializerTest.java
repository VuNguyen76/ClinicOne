package com.clinicone.config;

import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {
    @Mock StaffAccountRepository staffRepository;
    @Mock ClinicRoomRepository roomRepository;
    @Mock DoctorProfileRepository profileRepository;
    @Mock DoctorScheduleRepository scheduleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PatientAccountRepository patientRepository;
    @Mock com.clinicone.patientprofile.PatientProfileRepository patientProfileRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock ClinicServiceRepository clinicServiceRepository;
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void createsLocalStaffRoomProfileAndWeekdaySchedules() {
        when(staffRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.empty());
        when(staffRepository.save(any(StaffAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(roomRepository.findAllByOrderByCodeAsc()).thenReturn(List.of());
        when(roomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findByStaffAccount_Id(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(any(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findByPhone(any())).thenReturn(Optional.empty());
        when(patientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(any())).thenReturn(List.of());
        when(patientProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findByAppointmentCode(any())).thenReturn(Optional.empty());
        when(appointmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(clinicServiceRepository.save(any(ClinicService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new LocalDataInitializer(staffRepository, roomRepository, profileRepository, scheduleRepository,
                passwordEncoder, patientRepository, patientProfileRepository, appointmentRepository,
                clinicServiceRepository, jdbcTemplate,
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-patient-password").run();

        verify(staffRepository, org.mockito.Mockito.times(5)).save(any(StaffAccount.class));
        verify(roomRepository, org.mockito.Mockito.times(3)).save(any());
        verify(profileRepository, org.mockito.Mockito.times(3)).save(any());
        verify(scheduleRepository, org.mockito.Mockito.times(15)).save(any());
        ArgumentCaptor<ClinicService> serviceCaptor = ArgumentCaptor.forClass(ClinicService.class);
        verify(clinicServiceRepository).save(serviceCaptor.capture());
        assertThat(serviceCaptor.getValue().getEligibleDoctors()).hasSize(2);
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, org.mockito.Mockito.times(6)).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getAllValues()).containsExactlyInAnyOrder(
                "test-admin-password", "test-reception-password", "test-doctor-password", "test-doctor-password",
                "test-doctor-password", "test-patient-password");
        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(passwordEncoder);
    }
}

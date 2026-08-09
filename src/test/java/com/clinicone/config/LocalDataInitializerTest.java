package com.clinicone.config;

import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {
    @Mock StaffAccountRepository staffRepository;
    @Mock ClinicRoomRepository roomRepository;
    @Mock DoctorProfileRepository profileRepository;
    @Mock DoctorScheduleRepository scheduleRepository;
    @Mock PasswordEncoder passwordEncoder;

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

        new LocalDataInitializer(staffRepository, roomRepository, profileRepository, scheduleRepository,
                passwordEncoder).run();

        verify(staffRepository, org.mockito.Mockito.times(3)).save(any(StaffAccount.class));
        verify(roomRepository).save(any());
        verify(profileRepository).save(any());
        verify(scheduleRepository, org.mockito.Mockito.times(5)).save(any());
        verify(passwordEncoder, org.mockito.Mockito.times(3)).encode(any());
        verifyNoMoreInteractions(passwordEncoder);
    }
}

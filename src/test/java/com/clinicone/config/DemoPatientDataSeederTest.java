package com.clinicone.config;

import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoPatientDataSeederTest {
    @Mock PatientAccountRepository patientRepository;
    @Mock PatientProfileRepository profileRepository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void createsExactlyOneHundredClearlyMarkedSyntheticPatientAccountsAndProfiles() {
        when(patientRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(patientRepository.save(any(PatientAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(profileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-demo-password");

        seeder("pg-test.example/clinicone").run();

        ArgumentCaptor<PatientAccount> accounts = ArgumentCaptor.forClass(PatientAccount.class);
        ArgumentCaptor<PatientProfile> profiles = ArgumentCaptor.forClass(PatientProfile.class);
        verify(patientRepository, org.mockito.Mockito.times(100)).save(accounts.capture());
        verify(profileRepository, org.mockito.Mockito.times(100)).save(profiles.capture());
        assertThat(accounts.getAllValues()).extracting(PatientAccount::getFullName)
                .contains("Bệnh nhân Demo 0001", "Bệnh nhân Demo 0100");
        assertThat(accounts.getAllValues()).extracting(PatientAccount::getPhone)
                .contains("0998000001", "0998000100");
        assertThat(profiles.getAllValues()).allSatisfy(profile ->
                assertThat(profile.getIdentityNumber()).isNull());
    }

    @Test
    void refusesAHostOrDatabaseThatWasNotExplicitlyConfirmedBeforeAnyDatabaseRead() {
        DemoPatientDataSeeder seeder = new DemoPatientDataSeeder(patientRepository, profileRepository,
                passwordEncoder, "demo-password", "other-host/clinicone",
                "jdbc:postgresql://pg-test.example:5432/clinicone?sslmode=require");

        assertThatThrownBy(seeder::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact host/database confirmation");
        verifyNoInteractions(patientRepository, profileRepository, passwordEncoder);
    }

    @Test
    void allowsLocalDatabaseWithoutRemoteTargetConfirmation() {
        when(patientRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(patientRepository.save(any(PatientAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(profileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-demo-password");

        new DemoPatientDataSeeder(patientRepository, profileRepository, passwordEncoder,
                "demo-password", "", "jdbc:postgresql://localhost:5432/clinicone").run();

        verify(patientRepository, org.mockito.Mockito.times(100)).save(any(PatientAccount.class));
    }

    private DemoPatientDataSeeder seeder(String confirmedTarget) {
        return new DemoPatientDataSeeder(patientRepository, profileRepository, passwordEncoder,
                "demo-password", confirmedTarget,
                "jdbc:postgresql://pg-test.example:5432/clinicone?sslmode=require");
    }
}

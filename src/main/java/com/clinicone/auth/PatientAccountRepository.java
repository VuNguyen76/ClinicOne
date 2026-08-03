package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, UUID> {
    Optional<PatientAccount> findByEmail(String email);
    Optional<PatientAccount> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}

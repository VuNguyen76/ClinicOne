package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, UUID> {
    Optional<PatientAccount> findByPhone(String phone);
    boolean existsByPhone(String phone);
}

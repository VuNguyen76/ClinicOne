package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, UUID> {
    Optional<PatientAccount> findByPhone(String phone);
    boolean existsByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from PatientAccount account where account.id = :id")
    Optional<PatientAccount> findByIdForUpdate(@Param("id") UUID id);
}

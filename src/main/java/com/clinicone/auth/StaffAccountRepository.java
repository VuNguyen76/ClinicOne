package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {
    Optional<StaffAccount> findByUsernameIgnoreCase(String username);

    List<StaffAccount> findByRoleOrderByFullNameAsc(StaffRole role);

    List<StaffAccount> findAllByOrderByFullNameAsc();
}

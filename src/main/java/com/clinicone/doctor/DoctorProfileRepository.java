package com.clinicone.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {
    Optional<DoctorProfile> findByStaffAccount_Id(UUID staffId);

    List<DoctorProfile> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty);

    List<DoctorProfile> findAllByStaffAccount_IdInAndActiveTrue(Collection<UUID> staffIds);

    List<DoctorProfile> findAllByOrderByCreatedAtDesc();
}

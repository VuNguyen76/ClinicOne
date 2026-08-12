package com.clinicone.doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {
    Optional<DoctorProfile> findByStaffAccount_Id(UUID staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DoctorProfile profile where profile.staffAccount.id = :staffId")
    Optional<DoctorProfile> findByStaffAccount_IdForUpdate(@Param("staffId") UUID staffId);

    List<DoctorProfile> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty);

    List<DoctorProfile> findAllByStaffAccount_IdInAndActiveTrue(Collection<UUID> staffIds);

    List<DoctorProfile> findAllByOrderByCreatedAtDesc();
}

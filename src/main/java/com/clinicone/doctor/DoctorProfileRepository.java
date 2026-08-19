package com.clinicone.doctor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {
    @EntityGraph(attributePaths = {"staffAccount", "room"})
    Optional<DoctorProfile> findByStaffAccount_Id(UUID staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DoctorProfile profile where profile.staffAccount.id = :staffId")
    Optional<DoctorProfile> findByStaffAccount_IdForUpdate(@Param("staffId") UUID staffId);

    @EntityGraph(attributePaths = {"staffAccount", "room"})
    List<DoctorProfile> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty);

    @EntityGraph(attributePaths = {"staffAccount", "room"})
    List<DoctorProfile> findAllByStaffAccount_IdInAndActiveTrue(Collection<UUID> staffIds);

    @EntityGraph(attributePaths = {"staffAccount", "room"})
    List<DoctorProfile> findAllByOrderByCreatedAtDesc();
}

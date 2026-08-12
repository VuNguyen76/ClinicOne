package com.clinicone.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientNotificationRepository extends JpaRepository<PatientNotification, UUID> {
    List<PatientNotification> findByPatientAccountIdOrderByCreatedAtDesc(UUID patientAccountId);

    long countByPatientAccountIdAndReadAtIsNull(UUID patientAccountId);

    Optional<PatientNotification> findByIdAndPatientAccountId(UUID id, UUID patientAccountId);

    Optional<PatientNotification> findByEventKey(String eventKey);

    boolean existsByEventKey(String eventKey);
}

package com.clinicone.examination;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    List<MedicalRecord> findBySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(UUID patientId);

    Optional<MedicalRecord> findByIdAndSession_Appointment_Patient_IdAndSignedAtIsNotNull(UUID id, UUID patientId);
}

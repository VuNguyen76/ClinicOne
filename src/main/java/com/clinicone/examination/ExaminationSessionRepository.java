package com.clinicone.examination;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ExaminationSessionRepository extends JpaRepository<ExaminationSession, UUID> {
    List<ExaminationSession> findByAppointment_Patient_IdOrderByCreatedAtDesc(UUID patientId);

    Optional<ExaminationSession> findByAppointment_Id(UUID appointmentId);
}

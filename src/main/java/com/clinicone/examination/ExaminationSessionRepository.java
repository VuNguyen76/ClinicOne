package com.clinicone.examination;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface ExaminationSessionRepository extends JpaRepository<ExaminationSession, UUID> {
    List<ExaminationSession> findByAppointment_Patient_IdOrderByCreatedAtDesc(UUID patientId);

    Optional<ExaminationSession> findByAppointment_Id(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ExaminationSession session join fetch session.appointment appointment "
            + "where upper(appointment.appointmentCode) = upper(:appointmentCode)")
    Optional<ExaminationSession> findByAppointment_CodeForUpdate(@Param("appointmentCode") String appointmentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ExaminationSession session join fetch session.appointment where session.id = :id")
    Optional<ExaminationSession> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ExaminationSession session where session.appointment.id = :appointmentId")
    Optional<ExaminationSession> findByAppointment_IdForUpdate(@Param("appointmentId") UUID appointmentId);

    Optional<ExaminationSession> findByStartRequestKey(String startRequestKey);

    List<ExaminationSession> findByAppointment_IdIn(Collection<UUID> appointmentIds);
}

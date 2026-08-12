package com.clinicone.examination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    List<MedicalRecord> findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(UUID patientId);

    Optional<MedicalRecord> findByIdAndSession_Appointment_Patient_IdAndSignedAtIsNotNull(UUID id, UUID patientId);

    Optional<MedicalRecord> findBySession_Id(UUID sessionId);

    @Query("""
            select record from MedicalRecord record
            join record.session session
            join session.appointment appointment
            where appointment.patient.id = :patientId
              and appointment.patientProfile.id = :profileId
              and record.signedAt is not null
              and record.signedAt >= :fromAt
            order by record.signedAt desc
            """)
    List<MedicalRecord> findSignedForProfileSince(@Param("patientId") UUID patientId,
                                                   @Param("profileId") UUID profileId,
                                                   @Param("fromAt") Instant fromAt);

    @Query("""
            select record from MedicalRecord record
            join record.session session
            join session.appointment appointment
            where appointment.patient.id = :patientId
              and record.signedAt is not null
              and (:profileId is null or appointment.patientProfile.id = :profileId)
              and (:fromAt is null or record.signedAt >= :fromAt)
              and (:toExclusive is null or record.signedAt < :toExclusive)
            order by record.signedAt desc
            """)
    Page<MedicalRecord> findSignedHistory(@Param("patientId") UUID patientId,
                                          @Param("profileId") UUID profileId,
                                          @Param("fromAt") Instant fromAt,
                                          @Param("toExclusive") Instant toExclusive,
                                          Pageable pageable);
}

package com.clinicone.examination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    @EntityGraph(attributePaths = {"session", "session.appointment", "prescriptionLines"})
    List<MedicalRecord> findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
            UUID patientId);

    @EntityGraph(attributePaths = {"session", "session.appointment", "prescriptionLines"})
    List<MedicalRecord> findTop10BySession_Appointment_Patient_IdAndSession_Appointment_PatientProfileIsNullAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
            UUID patientId);

    @EntityGraph(attributePaths = {"session", "session.appointment", "prescriptionLines"})
    List<MedicalRecord> findTop10BySession_Appointment_PatientProfile_IdAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
            UUID patientProfileId);

    @EntityGraph(attributePaths = {"session", "session.appointment", "prescriptionLines"})
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
            order by record.signedAt desc, record.id desc
            """)
    List<MedicalRecord> findSignedForProfileSince(@Param("patientId") UUID patientId,
                                                   @Param("profileId") UUID profileId,
                                                   @Param("fromAt") Instant fromAt);

    @Query(value = """
            select record from MedicalRecord record
            join fetch record.session session
            join fetch session.appointment appointment
            where appointment.patient.id = :patientId
              and record.signedAt is not null
              and record.signedAt >= :fromAt
              and record.signedAt < :toExclusive
            order by record.signedAt desc, record.id desc
            """,
            countQuery = """
            select count(record) from MedicalRecord record
            join record.session session
            join session.appointment appointment
            where appointment.patient.id = :patientId
              and record.signedAt is not null
              and record.signedAt >= :fromAt
              and record.signedAt < :toExclusive
            """)
    Page<MedicalRecord> findSignedHistory(@Param("patientId") UUID patientId,
                                          @Param("fromAt") Instant fromAt,
                                          @Param("toExclusive") Instant toExclusive,
                                          Pageable pageable);

    @Query(value = """
            select record from MedicalRecord record
            join fetch record.session session
            join fetch session.appointment appointment
            where appointment.patient.id = :patientId
              and appointment.patientProfile.id = :profileId
              and record.signedAt is not null
              and record.signedAt >= :fromAt
              and record.signedAt < :toExclusive
            order by record.signedAt desc, record.id desc
            """,
            countQuery = """
            select count(record) from MedicalRecord record
            join record.session session
            join session.appointment appointment
            where appointment.patient.id = :patientId
              and appointment.patientProfile.id = :profileId
              and record.signedAt is not null
              and record.signedAt >= :fromAt
              and record.signedAt < :toExclusive
            """)
    Page<MedicalRecord> findSignedHistoryForProfile(@Param("patientId") UUID patientId,
                                                    @Param("profileId") UUID profileId,
                                                    @Param("fromAt") Instant fromAt,
                                                    @Param("toExclusive") Instant toExclusive,
                                                    Pageable pageable);

    @Query(value = """
            select record from MedicalRecord record
            join fetch record.session session
            join fetch session.appointment appointment
            left join fetch appointment.patientProfile profile
            left join fetch appointment.patient patient
            where appointment.doctorStaffId = :doctorId
              and record.signedAt is not null
              and (:fromDate is null or appointment.appointmentDate >= :fromDate)
              and (:toDate is null or appointment.appointmentDate <= :toDate)
              and (:search = ''
                   or lower(appointment.appointmentCode) like lower(concat('%', :search, '%'))
                   or lower(coalesce(profile.fullName, patient.fullName)) like lower(concat('%', :search, '%')))
            order by record.signedAt desc, record.id desc
            """,
            countQuery = """
            select count(record) from MedicalRecord record
            join record.session session
            join session.appointment appointment
            left join appointment.patientProfile profile
            left join appointment.patient patient
            where appointment.doctorStaffId = :doctorId
              and record.signedAt is not null
              and (:fromDate is null or appointment.appointmentDate >= :fromDate)
              and (:toDate is null or appointment.appointmentDate <= :toDate)
              and (:search = ''
                   or lower(appointment.appointmentCode) like lower(concat('%', :search, '%'))
                   or lower(coalesce(profile.fullName, patient.fullName)) like lower(concat('%', :search, '%')))
            """)
    Page<MedicalRecord> findSignedHistoryForDoctor(@Param("doctorId") UUID doctorId,
                                                    @Param("search") String search,
                                                    @Param("fromDate") LocalDate fromDate,
                                                    @Param("toDate") LocalDate toDate,
                                                    Pageable pageable);

    @EntityGraph(attributePaths = {"session", "session.appointment", "session.appointment.patientProfile",
            "session.appointment.patient", "prescriptionLines"})
    Optional<MedicalRecord> findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(UUID id, UUID doctorId);
}

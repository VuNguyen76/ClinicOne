package com.clinicone.appointment;

import com.clinicone.schedule.SlotBookingCount;
import com.clinicone.schedule.DoctorSlotBookingCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @EntityGraph(attributePaths = {"patient", "patientProfile"})
    List<Appointment> findByPatientIdOrderByAppointmentDateAscStartTimeAsc(UUID patientId);

    Optional<Appointment> findByPatientIdAndAppointmentDateAndStartTimeAndStatus(UUID patientId, LocalDate appointmentDate,
                                                                                   LocalTime startTime, AppointmentStatus status);

    long countBySpecialtyAndAppointmentDateAndStartTimeAndStatus(String specialty, LocalDate appointmentDate,
                                                                  LocalTime startTime, AppointmentStatus status);

    long countBySpecialtyAndAppointmentDateAndStartTimeAndStatusIn(String specialty, LocalDate appointmentDate,
                                                                    LocalTime startTime,
                                                                    Collection<AppointmentStatus> statuses);

    long countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(UUID doctorStaffId, LocalDate appointmentDate,
                                                                       LocalTime startTime, AppointmentStatus status);

    long countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatusIn(UUID doctorStaffId, LocalDate appointmentDate,
                                                                         LocalTime startTime,
                                                                         Collection<AppointmentStatus> statuses);

    long countByDoctorStaffIdAndAppointmentDateAndOverCapacityTrueAndStatusNot(UUID doctorStaffId,
                                                                                 LocalDate appointmentDate,
                                                                                 AppointmentStatus excludedStatus);

    @Query("""
            select new com.clinicone.schedule.SlotBookingCount(a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.specialty = :specialty
              and a.appointmentDate between :from and :to
              and a.status = :status
            group by a.appointmentDate, a.startTime
            """)
    List<SlotBookingCount> countBookedBySpecialtyAndDateRange(
            @Param("specialty") String specialty,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);

    @Query("""
            select new com.clinicone.schedule.SlotBookingCount(a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.specialty = :specialty
              and a.appointmentDate between :from and :to
              and a.status in :statuses
            group by a.appointmentDate, a.startTime
            """)
    List<SlotBookingCount> countBookedBySpecialtyAndDateRangeAndStatusIn(
            @Param("specialty") String specialty,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            select new com.clinicone.schedule.DoctorSlotBookingCount(
                a.doctorStaffId, a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.doctorStaffId in :doctorStaffIds
              and a.appointmentDate between :from and :to
              and a.status = :status
            group by a.doctorStaffId, a.appointmentDate, a.startTime
            """)
    List<DoctorSlotBookingCount> countBookedByDoctorsAndDateRange(
            @Param("doctorStaffIds") List<UUID> doctorStaffIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);

    @Query("""
            select new com.clinicone.schedule.DoctorSlotBookingCount(
                a.doctorStaffId, a.appointmentDate, a.startTime, count(a))
            from Appointment a
            where a.doctorStaffId in :doctorStaffIds
              and a.appointmentDate between :from and :to
              and a.status in :statuses
            group by a.doctorStaffId, a.appointmentDate, a.startTime
            """)
    List<DoctorSlotBookingCount> countBookedByDoctorsAndDateRangeAndStatusIn(
            @Param("doctorStaffIds") List<UUID> doctorStaffIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    Optional<Appointment> findByIdAndPatientId(UUID appointmentId, UUID patientId);

    Optional<Appointment> findByPatientIdAndCreationRequestKey(UUID patientId, String creationRequestKey);

    Optional<Appointment> findByPatientProfileIdAndCreationRequestKey(UUID patientProfileId, String creationRequestKey);

    Optional<Appointment> findByPatientIdAndCheckInRequestKey(UUID patientId, String checkInRequestKey);

    List<Appointment> findByPatientProfileId(UUID patientProfileId);

    boolean existsByPatientProfile_IdAndAppointmentDateAndStartTimeAndStatusIn(
            UUID patientProfileId, LocalDate appointmentDate, LocalTime startTime,
            Collection<AppointmentStatus> statuses);

    boolean existsByPatientIdAndAppointmentDateAndStartTime(
            UUID patientId, LocalDate appointmentDate, LocalTime startTime);

    @EntityGraph(attributePaths = {"patient", "patientProfile"})
    Optional<Appointment> findByAppointmentCode(String appointmentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") UUID id);

    List<Appointment> findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
            AppointmentStatus status, LocalDate from, LocalDate to);

    List<Appointment> findBySpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
            String specialty, LocalDate from, LocalDate to);

    List<Appointment> findByDoctorStaffIdAndSpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
            UUID doctorStaffId, String specialty, LocalDate from, LocalDate to);

    List<Appointment> findByDoctorStaffIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
            UUID doctorStaffId, LocalDate from, LocalDate to, AppointmentStatus status);

    @Query("""
            select a from Appointment a
            left join fetch a.patient p
            left join fetch a.patientProfile profile
            where a.appointmentDate = :appointmentDate
              and a.status in :statuses
            order by a.startTime asc
            """)
    List<Appointment> findByAppointmentDateAndStatusInOrderByStartTimeAsc(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            select a from Appointment a
            left join fetch a.patient p
            left join fetch a.patientProfile profile
            where a.status = :status
              and a.appointmentDate = :appointmentDate
              and (lower(a.appointmentCode) like lower(concat('%', :query, '%'))
                or p.phone like concat('%', :query, '%')
                or profile.phone like concat('%', :query, '%')
                or lower(p.fullName) like lower(concat('%', :query, '%'))
                or lower(profile.fullName) like lower(concat('%', :query, '%')))
            order by a.startTime asc
            """)
    List<Appointment> findReceptionCandidates(@Param("query") String query,
                                               @Param("appointmentDate") LocalDate appointmentDate,
                                               @Param("status") AppointmentStatus status);

    @Query("""
            select a from Appointment a
            left join fetch a.patient p
            left join fetch a.patientProfile profile
            where a.status in :statuses
              and a.appointmentDate = :appointmentDate
              and (lower(a.appointmentCode) like lower(concat('%', :query, '%'))
                or p.phone like concat('%', :query, '%')
                or profile.phone like concat('%', :query, '%')
                or lower(p.fullName) like lower(concat('%', :query, '%'))
                or lower(profile.fullName) like lower(concat('%', :query, '%')))
            order by a.startTime asc
            """)
    List<Appointment> findReceptionCandidatesByStatuses(@Param("query") String query,
                                                         @Param("appointmentDate") LocalDate appointmentDate,
                                                         @Param("statuses") Collection<AppointmentStatus> statuses);

    Optional<Appointment> findByPatientProfileIdAndAppointmentDateAndStartTimeAndStatus(
            UUID profileId, LocalDate appointmentDate, LocalTime startTime, AppointmentStatus status);

    @Query("""
            select a from Appointment a
            where a.patientProfile.id = :profileId
              and a.appointmentDate = :appointmentDate
              and a.startTime = :startTime
              and a.status in :statuses
            """)
    Optional<Appointment> findByPatientProfileIdAndAppointmentDateAndStartTimeAndStatusIn(
            @Param("profileId") UUID profileId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("statuses") Collection<AppointmentStatus> statuses);
}

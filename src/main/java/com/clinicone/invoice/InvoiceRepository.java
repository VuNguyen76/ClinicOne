package com.clinicone.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    boolean existsBySession_Id(UUID sessionId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patientProfile", "appointment.patient", "items"})
    Optional<Invoice> findByInvoiceCodeAndAppointment_Patient_Id(String invoiceCode, UUID patientId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patientProfile", "appointment.patient", "items"})
    Optional<Invoice> findByAppointment_AppointmentCodeAndAppointment_Patient_Id(String appointmentCode,
                                                                                  UUID patientId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patientProfile", "appointment.patient", "items"})
    List<Invoice> findByAppointment_Patient_IdOrderByCreatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patientProfile", "appointment.patient", "items"})
    List<Invoice> findByAppointment_PatientProfile_IdOrderByCreatedAtDesc(UUID patientProfileId);

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.invoiceCode = :invoiceCode")
    Optional<Invoice> findByInvoiceCodeForUpdate(@Param("invoiceCode") String invoiceCode);

    @EntityGraph(attributePaths = {"appointment", "appointment.patientProfile", "appointment.patient", "items"})
    Optional<Invoice> findBySession_Id(UUID sessionId);
}

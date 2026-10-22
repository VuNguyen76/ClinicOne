package com.clinicone.invoice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(UUID id, String invoiceCode, String appointmentCode, String patientName,
                              String patientPhone, String doctorName, String specialty,
                              long examFee, long labFee, long medicationFee, long discountAmount,
                              long totalAmount, long paidAmount, PaymentMethod paymentMethod,
                              InvoiceStatus status, String cashierName, Instant createdAt, Instant paidAt,
                              List<InvoiceItemResponse> items) {
    public static InvoiceResponse from(Invoice invoice) {
        var appointment = invoice.getAppointment();
        var profile = appointment.getPatientProfile();
        var patient = appointment.getPatient();
        String patientName = invoice.getPatientName() != null && !invoice.getPatientName().isBlank()
                ? invoice.getPatientName()
                : (profile == null ? patient.getFullName() : profile.getFullName());
        String patientPhone = invoice.getPatientPhone() != null && !invoice.getPatientPhone().isBlank()
                ? invoice.getPatientPhone()
                : (profile == null ? patient.getPhone() : profile.getPhone());
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceCode(), appointment.getAppointmentCode(),
                patientName, patientPhone, invoice.getDoctorName(),
                invoice.getSpecialty(), invoice.getExamFee(), invoice.getLabFee(), invoice.getMedicationFee(),
                invoice.getDiscountAmount(), invoice.getTotalAmount(), invoice.getPaidAmount(),
                invoice.getPaymentMethod(), invoice.getStatus(), invoice.getCashierName(), invoice.getCreatedAt(),
                invoice.getPaidAt(), invoice.getItems().stream().map(InvoiceItemResponse::from).toList());
    }
}

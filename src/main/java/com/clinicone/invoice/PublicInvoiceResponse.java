package com.clinicone.invoice;

import java.time.Instant;
import java.util.List;

public record PublicInvoiceResponse(String invoiceCode, String patientName, String doctorName,
                                    String specialty, long totalAmount, long paidAmount,
                                    InvoiceStatus status, Instant createdAt,
                                    List<PublicInvoiceItemResponse> items) {
    public static PublicInvoiceResponse from(Invoice invoice) {
        return new PublicInvoiceResponse(invoice.getInvoiceCode(),
                invoice.getPatientName(),
                invoice.getDoctorName(), invoice.getSpecialty(), invoice.getTotalAmount(),
                invoice.getPaidAmount(), invoice.getStatus(), invoice.getCreatedAt(),
                invoice.getItems().stream().map(PublicInvoiceItemResponse::from).toList());
    }
}

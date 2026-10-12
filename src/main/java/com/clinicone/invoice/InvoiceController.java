package com.clinicone.invoice;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
    private final InvoiceService service;

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<InvoiceResponse>> listForPatient(Authentication authentication) {
        return ResponseEntity.ok(service.listForPatient(authentication.getName()));
    }

    @GetMapping("/{invoiceCode}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<InvoiceResponse> lookupForPatient(Authentication authentication,
                                                             @PathVariable String invoiceCode) {
        return ResponseEntity.ok(service.lookupForPatient(authentication.getName(), invoiceCode));
    }

    @GetMapping("/appointments/{appointmentCode}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<InvoiceResponse> lookupForPatientByAppointment(Authentication authentication,
                                                                          @PathVariable String appointmentCode) {
        return ResponseEntity.ok(service.lookupForPatientByAppointment(authentication.getName(), appointmentCode));
    }

    @PostMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> createPaymentReceipt(Authentication authentication,
                                                                  @PathVariable UUID sessionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPaymentReceipt(sessionId, authentication.getName()));
    }

    @PostMapping("/appointments/{appointmentCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> createPaymentReceiptForAppointment(Authentication authentication,
                                                                               @PathVariable String appointmentCode) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPaymentReceiptForAppointment(appointmentCode, authentication.getName()));
    }

    @PostMapping("/{invoiceCode}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceResponse> markPaid(Authentication authentication,
                                                     @PathVariable String invoiceCode,
                                                     @Valid @RequestBody MarkInvoicePaidRequest request) {
        return ResponseEntity.ok(service.markPaid(invoiceCode, request, authentication.getName()));
    }
}

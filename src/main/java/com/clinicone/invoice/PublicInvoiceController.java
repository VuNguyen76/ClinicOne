package com.clinicone.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/invoices")
public class PublicInvoiceController {
    private final InvoiceService service;

    @PostMapping("/lookup")
    public ResponseEntity<PublicInvoiceResponse> lookupByCode(
            @Valid @RequestBody PublicInvoiceLookupRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.lookupPublicInvoice(request.invoiceCode()));
    }
}

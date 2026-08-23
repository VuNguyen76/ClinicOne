package com.clinicone.notification;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notifications/sms")
@PreAuthorize("hasRole('ADMIN')")
public class SmsDeliveryAdminController {
    private final SmsDeliveryService service;

    @GetMapping
    public List<SmsDeliveryResponse> listRecent() {
        return service.listRecent().stream().map(SmsDeliveryResponse::from).toList();
    }

    @PostMapping("/{id}/retry")
    public SmsDeliveryResponse retry(@PathVariable UUID id,
                                     @RequestHeader("Idempotency-Key") String requestKey) {
        return SmsDeliveryResponse.from(service.retry(id, requestKey));
    }
}

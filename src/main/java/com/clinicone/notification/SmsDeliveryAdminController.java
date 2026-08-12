package com.clinicone.notification;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/notifications/sms")
@PreAuthorize("hasRole('ADMIN')")
public class SmsDeliveryAdminController {
    private final SmsDeliveryService service;

    public SmsDeliveryAdminController(SmsDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<SmsDeliveryResponse> listRecent() {
        return service.listRecent().stream().map(SmsDeliveryResponse::from).toList();
    }
}

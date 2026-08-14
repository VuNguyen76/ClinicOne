package com.clinicone.schedule;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointment-holds")
@PreAuthorize("hasRole('PATIENT')")
public class AppointmentHoldController {
    private final AppointmentHoldService holdService;

    public AppointmentHoldController(AppointmentHoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping
    public ResponseEntity<AppointmentHoldResponse> create(Authentication authentication,
                                                           @RequestHeader(value = "X-ClinicOne-Session", required = false) String sessionKey,
                                                           @Valid @RequestBody CreateAppointmentHoldRequest request) {
        return ResponseEntity.status(201).body(holdService.create(authentication.getName(), request, sessionKey));
    }
}

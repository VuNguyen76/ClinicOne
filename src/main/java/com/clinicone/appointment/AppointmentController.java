package com.clinicone.appointment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(appointmentService.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(Authentication authentication,
                                                       @Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(201).body(appointmentService.create(authentication.getName(), request));
    }
}

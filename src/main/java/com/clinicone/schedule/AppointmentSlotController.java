package com.clinicone.schedule;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/appointment-slots")
public class AppointmentSlotController {
    private final AppointmentAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<List<AvailableSlotResponse>> list(
            @RequestParam String specialty,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID serviceId) {
        return ResponseEntity.ok(availabilityService.find(specialty, from, to, serviceId));
    }
}

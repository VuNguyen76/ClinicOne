package com.clinicone.rescheduling;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/doctor-time-off")
@PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
public class DoctorTimeOffController {
    private final DoctorTimeOffService service;

    @GetMapping
    public List<DoctorTimeOffResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public DoctorTimeOffResponse create(@Valid @RequestBody CreateDoctorTimeOffRequest request) {
        return service.create(request);
    }
}

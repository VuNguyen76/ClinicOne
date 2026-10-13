package com.clinicone.schedule;

import lombok.RequiredArgsConstructor;

import com.clinicone.auth.AuthenticatedIds;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/doctor/schedule-templates")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorScheduleTemplateController {
    private final ScheduleTemplateService service;

    @GetMapping
    public ResponseEntity<List<ScheduleTemplateResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.listForDoctor(AuthenticatedIds.staff(authentication.getName())));
    }
}

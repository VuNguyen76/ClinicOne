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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import com.clinicone.auth.AuthException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/doctor-time-off")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR', 'RECEPTIONIST')")
public class DoctorTimeOffController {
    private final DoctorTimeOffService service;

    @GetMapping
    public List<DoctorTimeOffResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR')")
    public DoctorTimeOffResponse create(
            @Valid @RequestBody CreateDoctorTimeOffRequest request,
            Authentication authentication) {
        if (authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))
                && authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COORDINATOR"))) {
            try {
                UUID callerStaffId = UUID.fromString(authentication.getName());
                if (!callerStaffId.equals(request.doctorId())) {
                    throw new AuthException(HttpStatus.FORBIDDEN,
                            "DOCTOR_TIME_OFF_SELF_ONLY", "Bác sĩ chỉ được đăng ký nghỉ phép cho chính mình.");
                }
            } catch (IllegalArgumentException e) {
                // Ignore if name isn't UUID format
            }
        }
        return service.create(request);
    }
}

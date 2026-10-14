package com.clinicone.rescheduling;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        assertDoctorOwnsTimeOff(authentication, request.doctorId());
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR')")
    public DoctorTimeOffResponse update(@PathVariable UUID id,
            @Valid @RequestBody CreateDoctorTimeOffRequest request, Authentication authentication) {
        assertDoctorOwnsTimeOff(authentication, request.doctorId());
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR')")
    public DoctorTimeOffResponse cancel(@PathVariable UUID id, Authentication authentication) {
        UUID doctorId = service.doctorStaffId(id);
        assertDoctorOwnsTimeOff(authentication, doctorId);
        return service.cancel(id);
    }

    private void assertDoctorOwnsTimeOff(Authentication authentication, UUID doctorId) {
        if (authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))
                && authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_COORDINATOR"))) {
            try {
                UUID callerStaffId = UUID.fromString(authentication.getName());
                if (!callerStaffId.equals(doctorId)) {
                    throw new AuthException(HttpStatus.FORBIDDEN,
                            "DOCTOR_TIME_OFF_SELF_ONLY", "Bác sĩ chỉ được thao tác lịch nghỉ của chính mình.");
                }
            } catch (IllegalArgumentException e) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "DOCTOR_TIME_OFF_SELF_ONLY", "Không xác định được tài khoản bác sĩ.");
            }
        }
    }
}

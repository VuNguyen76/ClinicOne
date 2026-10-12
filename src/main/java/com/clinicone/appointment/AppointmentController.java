package com.clinicone.appointment;

import lombok.RequiredArgsConstructor;

import com.clinicone.auth.AuthException;
import com.clinicone.validation.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasRole('PATIENT')")
public class AppointmentController {
    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(appointmentService.list(authentication.getName()));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> get(Authentication authentication,
                                                    @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.get(authentication.getName(), appointmentId.toString()));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(Authentication authentication,
                                                       @Valid @RequestBody CreateAppointmentRequest request,
                                                       HttpServletRequest httpRequest,
                                                       @RequestHeader(value = "X-ClinicOne-Session", required = false)
                                                       String sessionKey,
                                                       @IdempotencyKey
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        Object serverSessionId = httpRequest.getAttribute("clinicOneLoginSessionId");
        String effectiveSessionKey = serverSessionId == null ? sessionKey : serverSessionId.toString();
        AppointmentResponse response = effectiveSessionKey == null || effectiveSessionKey.isBlank()
                ? requestKey == null || requestKey.isBlank()
                    ? appointmentService.create(authentication.getName(), request)
                    : appointmentService.create(authentication.getName(), request, requestKey)
                : appointmentService.create(authentication.getName(), request, requestKey, effectiveSessionKey);
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/{appointmentId}/cancel")
    public ResponseEntity<Void> cancel(Authentication authentication,
                                       @PathVariable UUID appointmentId,
                                       @Valid @RequestBody(required = false) CancelAppointmentRequest request,
                                       @IdempotencyKey
                                       @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        appointmentService.cancel(authentication.getName(), appointmentId.toString(), request, requestKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{appointmentId}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(Authentication authentication,
                                                           @PathVariable UUID appointmentId,
                                                           @Valid @RequestBody RescheduleAppointmentRequest request) {
        throw new AuthException(HttpStatus.GONE,
                "PATIENT_RESCHEDULE_NOT_SUPPORTED",
                "Người bệnh chỉ xác nhận lịch thay thế do phòng khám đề xuất; vui lòng liên hệ quầy nếu cần đổi lịch.");
    }
}

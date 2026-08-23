package com.clinicone.queue;

import lombok.RequiredArgsConstructor;

import com.clinicone.validation.IdempotencyKey;
import jakarta.validation.Valid;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffRole;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class QueueController {
    private final QueueService queueService;

    @PostMapping("/rooms/{roomCode}/queue/check-in")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<QueueTicketResponse> checkIn(Authentication authentication,
                                                        @PathVariable String roomCode,
                                                        @Valid @RequestBody QueueCheckInRequest request,
                                                        @IdempotencyKey
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return ResponseEntity.ok(requestKey == null || requestKey.isBlank()
                ? queueService.checkIn(authentication.getName(), roomCode, request.appointmentId())
                : queueService.checkIn(authentication.getName(), roomCode, request.appointmentId(), requestKey));
    }

    @GetMapping("/patient/queue")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<QueueTicketResponse>> patientQueue(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(queueService.listForPatient(authentication.getName(), date));
    }

    @GetMapping("/rooms/{roomCode}/queue")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<QueueTicketResponse>> list(
            Authentication authentication,
            @PathVariable String roomCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(queueService.listForStaff(roomCode, date, authentication.getName(), staffRole(authentication)));
    }

    @GetMapping("/doctor/queue")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorQueueResponse> doctorQueue(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(queueService.doctorQueue(date, authentication.getName()));
    }

    @PostMapping("/doctor/queue/call-next")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QueueTicketResponse> callNext(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(queueService.callNext(authentication.getName(), date));
    }

    @PostMapping("/queue/{ticketId}/call")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QueueTicketResponse> call(Authentication authentication, @PathVariable UUID ticketId) {
        return ResponseEntity.ok(queueService.call(ticketId, authentication.getName()));
    }

    @PostMapping("/queue/{ticketId}/skip")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QueueTicketResponse> skip(Authentication authentication, @PathVariable UUID ticketId,
                                                    @Valid @RequestBody(required = false) QueueSkipRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(queueService.skip(ticketId, authentication.getName(), reason));
    }

    @PostMapping("/queue/{ticketId}/leave")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'RECEPTIONIST')")
    public ResponseEntity<QueueTicketResponse> leaveBeforeExam(
            Authentication authentication, @PathVariable UUID ticketId,
            @Valid @RequestBody QueueLeaveRequest request) {
        return ResponseEntity.ok(queueService.leaveBeforeExam(ticketId, request.reason(), authentication.getName()));
    }

    @PostMapping("/queue/{ticketId}/facility-unavailable")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'RECEPTIONIST')")
    public ResponseEntity<QueueTicketResponse> facilityUnavailable(
            Authentication authentication, @PathVariable UUID ticketId,
            @Valid @RequestBody QueueLeaveRequest request) {
        return ResponseEntity.ok(queueService.markFacilityUnavailable(ticketId, request.reason(), authentication.getName()));
    }

    @PostMapping("/queue/{ticketId}/adjust")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<QueueTicketResponse> adjust(Authentication authentication,
                                                       @PathVariable UUID ticketId,
                                                       @Valid @RequestBody QueueAdjustmentRequest request) {
        return ResponseEntity.ok(queueService.adjust(ticketId, request, authentication.getName()));
    }

    @PostMapping("/queue/{ticketId}/start")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QueueTicketResponse> start(Authentication authentication, @PathVariable UUID ticketId) {
        throw new AuthException(HttpStatus.GONE, "DOCTOR_START_ENDPOINT_REPLACED",
                "Hãy dùng chức năng bắt đầu khám trong hồ sơ bác sĩ.");
    }

    @PostMapping("/queue/{ticketId}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QueueTicketResponse> complete(Authentication authentication, @PathVariable UUID ticketId) {
        throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_COMPLETE_VIA_SIGN",
                "Bác sĩ cần ký phiếu khám để hệ thống tự hoàn tất lượt.");
    }

    private StaffRole staffRole(Authentication authentication) {
        List<StaffRole> roles = authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(value -> {
                    try {
                        return StaffRole.valueOf(value);
                    } catch (IllegalArgumentException exception) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        return roles.stream()
                .filter(role -> role == StaffRole.DOCTOR)
                .findFirst()
                .or(() -> roles.stream().findFirst())
                .orElseThrow(() -> new AccessDeniedException("Staff role required"));
    }
}

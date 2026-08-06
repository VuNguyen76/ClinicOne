package com.clinicone.queue;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/rooms/{roomCode}/queue/check-in")
    public ResponseEntity<QueueTicketResponse> checkIn(Authentication authentication,
                                                        @PathVariable String roomCode,
                                                        @Valid @RequestBody QueueCheckInRequest request) {
        return ResponseEntity.ok(queueService.checkIn(authentication.getName(), roomCode, request.appointmentId()));
    }

    @GetMapping("/rooms/{roomCode}/queue")
    public ResponseEntity<List<QueueTicketResponse>> list(
            @PathVariable String roomCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(queueService.list(roomCode, date));
    }

    @PostMapping("/queue/{ticketId}/call")
    public ResponseEntity<QueueTicketResponse> call(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(queueService.call(ticketId));
    }

    @PostMapping("/queue/{ticketId}/skip")
    public ResponseEntity<QueueTicketResponse> skip(@PathVariable UUID ticketId,
                                                    @Valid @RequestBody(required = false) QueueSkipRequest request) {
        return ResponseEntity.ok(queueService.skip(ticketId, request == null ? null : request.reason()));
    }

    @PostMapping("/queue/{ticketId}/start")
    public ResponseEntity<QueueTicketResponse> start(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(queueService.start(ticketId));
    }

    @PostMapping("/queue/{ticketId}/complete")
    public ResponseEntity<QueueTicketResponse> complete(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(queueService.complete(ticketId));
    }
}

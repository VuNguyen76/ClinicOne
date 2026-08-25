package com.clinicone.notification;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('PATIENT')")
public class PatientNotificationController {
    private final PatientNotificationService service;

    @GetMapping
    public ResponseEntity<List<PatientNotificationResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(authentication.getName()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", service.unreadCount(authentication.getName())));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable UUID notificationId) {
        service.markRead(authentication.getName(), notificationId.toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        service.markAllRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

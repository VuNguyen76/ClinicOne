package com.clinicone.notification;

import com.clinicone.appointment.Appointment;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Replays recent signed-record notifications when a reception-only profile is
 * linked to a patient account. The notification event key keeps the replay
 * idempotent if activation is retried.
 */
@Service
public class PatientNotificationBackfillService {
    private static final Duration HISTORY_WINDOW = Duration.ofDays(30);

    private final MedicalRecordRepository recordRepository;
    private final PatientNotificationService notificationService;
    private final Clock clock;

    public PatientNotificationBackfillService(MedicalRecordRepository recordRepository,
                                              PatientNotificationService notificationService,
                                              Clock clock) {
        this.recordRepository = recordRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public void notifyRecentSignedRecords(UUID patientId, UUID profileId) {
        if (patientId == null || profileId == null) {
            return;
        }
        Instant fromAt = Instant.now(clock).minus(HISTORY_WINDOW);
        for (MedicalRecord record : recordRepository.findSignedForProfileSince(patientId, profileId, fromAt)) {
            if (record.getId() == null || record.getSession() == null) {
                continue;
            }
            Appointment appointment = record.getSession().getAppointment();
            if (appointment == null) {
                continue;
            }
            notificationService.notifyMedicalRecordSigned(patientId, record.getId(),
                    appointment.getAppointmentCode(), appointment.getDoctorName(), appointment.getSpecialty());
        }
    }
}

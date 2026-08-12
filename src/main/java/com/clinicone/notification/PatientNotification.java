package com.clinicone.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_patient_notifications_event", columnNames = "event_key")
})
public class PatientNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_account_id", nullable = false)
    private UUID patientAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PatientNotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "target_url", nullable = false, length = 300)
    private String targetUrl;

    @Column(name = "event_key", nullable = false, length = 120)
    private String eventKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected PatientNotification() {
    }

    private PatientNotification(UUID patientAccountId, PatientNotificationType type, String title,
                                String message, String targetUrl, String eventKey) {
        this.patientAccountId = patientAccountId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.targetUrl = targetUrl;
        this.eventKey = eventKey;
    }

    public static PatientNotification recordSigned(UUID patientAccountId, UUID recordId, String appointmentCode,
                                                     String doctorName, String specialty) {
        String appointmentLabel = appointmentCode == null || appointmentCode.isBlank()
                ? "lượt khám của bạn" : "lịch hẹn " + appointmentCode;
        String doctorLabel = doctorName == null || doctorName.isBlank() ? "bác sĩ phụ trách" : doctorName;
        String specialtyLabel = specialty == null || specialty.isBlank() ? "" : " tại " + specialty;
        return new PatientNotification(patientAccountId, PatientNotificationType.MEDICAL_RECORD_SIGNED,
                "Phiếu khám đã có kết quả",
                "" + doctorLabel + " đã ký phiếu khám cho " + appointmentLabel + specialtyLabel + ".",
                "/medical-records/" + recordId,
                "MEDICAL_RECORD_SIGNED:" + recordId);
    }

    public static PatientNotification appointmentCreated(UUID patientAccountId, UUID appointmentId,
                                                         String appointmentCode, String specialty,
                                                         String doctorName, String date, String time) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_CREATED,
                "Đặt lịch thành công",
                "Lịch hẹn " + appointmentCode + " với " + doctorName + " đã được ghi nhận vào " + date + " lúc " + time + ".",
                "/appointments/" + appointmentId,
                "APPOINTMENT_CREATED:" + appointmentId);
    }

    public static PatientNotification appointmentCancelled(UUID patientAccountId, UUID appointmentId,
                                                            String appointmentCode) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_CANCELLED,
                "Lịch hẹn đã được hủy",
                "Lịch hẹn " + appointmentCode + " đã được hủy theo yêu cầu của bạn.",
                "/appointments/" + appointmentId,
                "APPOINTMENT_CANCELLED:" + appointmentId);
    }

    public static PatientNotification appointmentRescheduled(UUID patientAccountId, UUID appointmentId,
                                                              String appointmentCode, String date, String time,
                                                              String previousDate, String previousTime) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_RESCHEDULED,
                "Lịch hẹn đã được đổi",
                "Lịch hẹn " + appointmentCode + " đã chuyển sang " + date + " lúc " + time + ".",
                "/appointments/" + appointmentId,
                "APPOINTMENT_RESCHEDULED:" + appointmentId + ":" + previousDate + "T" + previousTime + "->" + date + "T" + time);
    }

    public static PatientNotification appointmentRescheduleRequired(UUID patientAccountId, UUID appointmentId,
                                                                      String appointmentCode) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_RESCHEDULE_REQUIRED,
                "Lịch hẹn cần chọn lại giờ",
                "Lịch hẹn " + appointmentCode + " cần được sắp xếp lại. Vui lòng mở ứng dụng hoặc liên hệ quầy.",
                "/appointments/" + appointmentId,
                "APPOINTMENT_RESCHEDULE_REQUIRED:" + appointmentId);
    }

    public static PatientNotification appointmentReminder(UUID patientAccountId, UUID appointmentId,
                                                          String appointmentCode, String specialty,
                                                          String doctorName, String date, String time, int hours) {
        PatientNotificationType type = hours == 24
                ? PatientNotificationType.APPOINTMENT_REMINDER_24H
                : PatientNotificationType.APPOINTMENT_REMINDER_2H;
        String suffix = hours == 24 ? "24 giờ" : "2 giờ";
        return new PatientNotification(patientAccountId, type,
                "Nhắc lịch khám",
                "Lịch hẹn " + appointmentCode + " vào " + date + " lúc " + time
                        + " (còn khoảng " + suffix + ").",
                "/appointments/" + appointmentId,
                type.name() + ":" + appointmentId);
    }

    public static PatientNotification appointmentLateWarning(UUID patientAccountId, UUID appointmentId,
                                                              String appointmentCode) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_LATE_WARNING,
                "Lịch hẹn đã quá giờ",
                "Lịch hẹn " + appointmentCode + " đã quá giờ. Vui lòng đến quầy để được hỗ trợ.",
                "/appointments/" + appointmentId,
                "APPOINTMENT_LATE_WARNING:" + appointmentId);
    }

    public static PatientNotification appointmentAbsent(UUID patientAccountId, UUID appointmentId,
                                                         String appointmentCode) {
        return new PatientNotification(patientAccountId, PatientNotificationType.APPOINTMENT_ABSENT,
                "Đã ghi nhận vắng mặt",
                "Lịch hẹn " + appointmentCode + " đã được ghi nhận vắng mặt. Vui lòng liên hệ quầy để đặt lịch mới.",
                "/appointments/" + appointmentId,
                "APPOINTMENT_ABSENT:" + appointmentId);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getPatientAccountId() { return patientAccountId; }
    public PatientNotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTargetUrl() { return targetUrl; }
    public String getEventKey() { return eventKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}

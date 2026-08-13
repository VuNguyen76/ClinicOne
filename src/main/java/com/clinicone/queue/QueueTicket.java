package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "queue_tickets", indexes = {
        @Index(name = "idx_queue_tickets_room_date_status", columnList = "room_id,queue_date,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_queue_tickets_appointment", columnNames = "appointment_id"),
        @UniqueConstraint(name = "uk_queue_tickets_room_date_number", columnNames = {"room_id", "queue_date", "queue_number"})
})
public class QueueTicket {
    public static final int MAX_QUEUE_NUMBER = 9999;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ClinicRoom room;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueTicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_outcome", length = 40)
    private QueueClosureOutcome closureOutcome;

    /** Read-only presence signal; it is not a second queue lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "presence_status", length = 24)
    private QueuePresenceStatus presenceStatus = QueuePresenceStatus.READY;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    // Nullable keeps ddl-auto update compatible with queue rows created before recall tracking.
    @Column(name = "call_count")
    private Integer callCount = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "skip_reason", length = 250)
    private String skipReason;

    @Column(name = "exception_reason", length = 250)
    private String exceptionReason;

    @Column(name = "routing_doctor_staff_id")
    private UUID routingDoctorStaffId;

    @Column(name = "routing_doctor_name", length = 120)
    private String routingDoctorName;

    @Column(name = "routing_specialty", length = 120)
    private String routingSpecialty;

    // Nullable keeps ddl-auto update compatible with queue rows created before
    // priority was introduced; legacy null values are read as the Java default false.
    @Column(name = "priority_flag")
    private boolean priority;

    @Version
    @Column(nullable = false)
    private long version;

    protected QueueTicket() {
    }

    private QueueTicket(Appointment appointment, ClinicRoom room, LocalDate queueDate, int queueNumber) {
        this.appointment = appointment;
        this.room = room;
        this.queueDate = queueDate;
        this.queueNumber = queueNumber;
        this.status = QueueTicketStatus.WAITING;
        this.presenceStatus = QueuePresenceStatus.READY;
        this.checkedInAt = Instant.now();
    }

    public static QueueTicket create(Appointment appointment, ClinicRoom room, LocalDate queueDate, int queueNumber) {
        return create(appointment, room, queueDate, queueNumber, null);
    }

    public static QueueTicket create(Appointment appointment, ClinicRoom room, LocalDate queueDate,
                                     int queueNumber, String exceptionReason) {
        if (queueNumber < 1 || queueNumber > MAX_QUEUE_NUMBER) {
            throw new IllegalArgumentException("Số thứ tự phải từ 1 đến " + MAX_QUEUE_NUMBER);
        }
        QueueTicket ticket = new QueueTicket(appointment, room, queueDate, queueNumber);
        ticket.recordExceptionReason(exceptionReason);
        return ticket;
    }

    public void recordExceptionReason(String reason) {
        this.exceptionReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public long getVersion() { return version; }

    public void moveTo(ClinicRoom targetRoom, UUID doctorStaffId, String doctorName, String specialty,
                       int targetQueueNumber) {
        if (status != QueueTicketStatus.WAITING) {
            throw new IllegalStateException("Chỉ được điều chỉnh lượt đang chờ");
        }
        this.room = targetRoom;
        this.routingDoctorStaffId = doctorStaffId;
        this.routingDoctorName = doctorName == null || doctorName.isBlank() ? null : doctorName.trim();
        this.routingSpecialty = specialty == null || specialty.isBlank() ? null : specialty.trim();
        this.queueNumber = targetQueueNumber;
    }

    public void setPriority(boolean priority) {
        if (status != QueueTicketStatus.WAITING) {
            throw new IllegalStateException("Chỉ được điều chỉnh lượt đang chờ");
        }
        this.priority = priority;
    }

    public void call() {
        if (status != QueueTicketStatus.WAITING && status != QueueTicketStatus.SKIPPED) {
            throw new IllegalStateException("Chỉ được gọi lượt đang chờ hoặc đã bỏ qua");
        }
        if (getPresenceStatus() == QueuePresenceStatus.RETURN_REQUIRED) {
            throw new IllegalStateException("Bệnh nhân chưa quay lại sau khi được gọi");
        }
        status = QueueTicketStatus.CALLED;
        calledAt = Instant.now();
        if (callCount == null) {
            callCount = 0;
        }
        callCount++;
        skipReason = null;
    }

    public void skip(String reason) {
        if (status != QueueTicketStatus.CALLED) {
            throw new IllegalStateException("Chỉ được bỏ qua lượt đang được gọi");
        }
        // "Gọi lại sau" đưa người bệnh về trạng thái chờ, nhưng đánh dấu phải quay lại.
        status = QueueTicketStatus.WAITING;
        presenceStatus = QueuePresenceStatus.RETURN_REQUIRED;
        skipReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public void markReturned(Instant returnedAt) {
        if (status != QueueTicketStatus.WAITING
                || getPresenceStatus() != QueuePresenceStatus.RETURN_REQUIRED) {
            return;
        }
        presenceStatus = QueuePresenceStatus.READY;
        this.returnedAt = returnedAt;
    }

    public void startService() {
        if (status != QueueTicketStatus.CALLED) {
            throw new IllegalStateException("Chỉ được bắt đầu khám từ lượt đang được gọi");
        }
        status = QueueTicketStatus.IN_SERVICE;
    }

    public void complete() {
        if (status != QueueTicketStatus.CALLED && status != QueueTicketStatus.IN_SERVICE) {
            throw new IllegalStateException("Lượt chưa sẵn sàng để hoàn tất");
        }
        status = QueueTicketStatus.COMPLETED;
        closureOutcome = QueueClosureOutcome.EXAMINATION_COMPLETED;
        completedAt = Instant.now();
    }

    public void stopService(String reason) {
        if (status != QueueTicketStatus.IN_SERVICE) {
            throw new IllegalStateException("Chỉ có thể dừng lượt đang khám.");
        }
        status = QueueTicketStatus.COMPLETED;
        closureOutcome = QueueClosureOutcome.EXAMINATION_STOPPED;
        skipReason = reason == null || reason.isBlank() ? null : reason.trim();
        completedAt = Instant.now();
    }

    public void returnForCorrectProfile() {
        if (status != QueueTicketStatus.IN_SERVICE) {
            throw new IllegalStateException("Chỉ có thể trả lại hàng đợi từ lượt đang khám.");
        }
        status = QueueTicketStatus.WAITING;
        presenceStatus = QueuePresenceStatus.READY;
        completedAt = null;
        skipReason = null;
        closureOutcome = null;
    }

    public void leaveBeforeExam(String reason) {
        if (status == QueueTicketStatus.LEFT_BEFORE_EXAM) {
            return;
        }
        if (status != QueueTicketStatus.WAITING && status != QueueTicketStatus.CALLED) {
            throw new IllegalStateException("Chỉ có thể đóng lượt trước khi bắt đầu khám.");
        }
        status = QueueTicketStatus.LEFT_BEFORE_EXAM;
        closureOutcome = QueueClosureOutcome.LEFT_BEFORE_EXAM;
        skipReason = reason == null || reason.isBlank() ? null : reason.trim();
        completedAt = Instant.now();
    }

    public void closeFacilityUnavailable(String reason) {
        if (status != QueueTicketStatus.WAITING && status != QueueTicketStatus.CALLED) {
            throw new IllegalStateException("Chỉ có thể đóng lượt chưa bắt đầu khám.");
        }
        status = QueueTicketStatus.COMPLETED;
        closureOutcome = QueueClosureOutcome.FACILITY_UNAVAILABLE;
        skipReason = reason == null || reason.isBlank() ? null : reason.trim();
        completedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (checkedInAt == null) {
            checkedInAt = Instant.now();
        }
        if (callCount == null || callCount < 0) {
            callCount = 0;
        }
        if (presenceStatus == null) {
            presenceStatus = QueuePresenceStatus.READY;
        }
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        if (presenceStatus == null) {
            presenceStatus = QueuePresenceStatus.READY;
        }
    }

    public UUID getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public ClinicRoom getRoom() { return room; }
    public LocalDate getQueueDate() { return queueDate; }
    public int getQueueNumber() { return queueNumber; }
    public QueueTicketStatus getStatus() { return status; }
    public QueueClosureOutcome getClosureOutcome() { return closureOutcome; }
    public String getClosureOutcomeLabel() { return closureOutcome == null ? null : closureOutcome.label(); }
    public QueuePresenceStatus getPresenceStatus() {
        return presenceStatus == null ? QueuePresenceStatus.READY : presenceStatus;
    }
    public String getPresenceLabel() { return getPresenceStatus().label(); }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public int getCallCount() { return callCount == null ? 0 : callCount; }
    public Instant getCompletedAt() { return completedAt; }
    public String getSkipReason() { return skipReason; }
    public String getExceptionReason() { return exceptionReason; }
    public UUID getRoutingDoctorStaffId() { return routingDoctorStaffId; }
    public String getRoutingDoctorName() { return routingDoctorName; }
    public String getRoutingSpecialty() { return routingSpecialty; }
    public boolean isPriority() { return priority; }
    public UUID getEffectiveDoctorStaffId() {
        return routingDoctorStaffId == null ? appointment.getDoctorStaffId() : routingDoctorStaffId;
    }
    public String getEffectiveDoctorName() {
        return routingDoctorName == null || routingDoctorName.isBlank()
                ? appointment.getDoctorName() : routingDoctorName;
    }
    public String getEffectiveSpecialty() {
        return routingSpecialty == null || routingSpecialty.isBlank()
                ? appointment.getSpecialty() : routingSpecialty;
    }
}

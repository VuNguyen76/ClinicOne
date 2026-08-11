package com.clinicone.examination;

import com.clinicone.queue.QueueTicket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sealed clinical snapshot created when a doctor starts the wrong patient
 * profile.  It is deliberately separate from MedicalRecord so it can never
 * appear in patient history or be reused when the correct examination begins.
 */
@Entity
@Table(name = "wrong_profile_incidents")
public class WrongProfileIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_medical_record_id")
    private UUID sourceMedicalRecordId;

    @Column(name = "examination_session_id", nullable = false)
    private UUID examinationSessionId;

    @Column(name = "queue_ticket_id", nullable = false)
    private UUID queueTicketId;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "doctor_staff_id", nullable = false)
    private UUID doctorStaffId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "draft_reason", length = 2000)
    private String draftReason;

    @Column(name = "examination_notes", length = 2000)
    private String examinationNotes;

    @Column(name = "diagnosis", length = 2000)
    private String diagnosis;

    @Column(name = "conclusion", length = 2000)
    private String conclusion;

    @Column(name = "treatment_plan", length = 2000)
    private String treatmentPlan;

    @Lob
    @Column(name = "prescription_snapshot")
    private String prescriptionSnapshot;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_days")
    private Integer followUpDays;

    @Column(name = "follow_up_note", length = 500)
    private String followUpNote;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "sealed_at", nullable = false)
    private Instant sealedAt;

    protected WrongProfileIncident() {
    }

    public static WrongProfileIncident seal(MedicalRecord record, QueueTicket ticket,
                                            ExaminationSession session, UUID doctorStaffId, String reason) {
        WrongProfileIncident incident = new WrongProfileIncident();
        incident.sourceMedicalRecordId = record == null ? null : record.getId();
        incident.examinationSessionId = session.getId();
        incident.queueTicketId = ticket.getId();
        incident.appointmentId = ticket.getAppointment().getId();
        incident.doctorStaffId = doctorStaffId;
        incident.reason = reason;
        incident.startedAt = session.getStartedAt();
        if (record != null) {
            incident.draftReason = record.getReason();
            incident.examinationNotes = record.getExaminationNotes();
            incident.diagnosis = record.getDiagnosis();
            incident.conclusion = record.getConclusion();
            incident.treatmentPlan = record.getTreatmentPlan();
            incident.prescriptionSnapshot = snapshotPrescription(record);
            incident.followUpDate = record.getFollowUpDate();
            incident.followUpDays = record.getFollowUpDays();
            incident.followUpNote = record.getFollowUpNote();
        }
        return incident;
    }

    private static String snapshotPrescription(MedicalRecord record) {
        if (!record.getPrescriptionLines().isEmpty()) {
            return record.getPrescriptionLines().stream()
                    .map(line -> line.getMedicationName() + " | " + line.getDosage() + " | "
                            + line.getQuantity() + " | " + line.getInstructions())
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse(null);
        }
        return record.getPrescription();
    }

    @PrePersist
    void onCreate() {
        if (sealedAt == null) {
            sealedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getSourceMedicalRecordId() { return sourceMedicalRecordId; }
    public UUID getExaminationSessionId() { return examinationSessionId; }
    public UUID getQueueTicketId() { return queueTicketId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getDoctorStaffId() { return doctorStaffId; }
    public String getReason() { return reason; }
    public String getDraftReason() { return draftReason; }
    public String getExaminationNotes() { return examinationNotes; }
    public String getDiagnosis() { return diagnosis; }
    public String getConclusion() { return conclusion; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public String getPrescriptionSnapshot() { return prescriptionSnapshot; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public Integer getFollowUpDays() { return followUpDays; }
    public String getFollowUpNote() { return followUpNote; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSealedAt() { return sealedAt; }
}

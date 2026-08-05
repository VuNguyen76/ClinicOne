package com.clinicone.examination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_medical_records_session", columnNames = "examination_session_id")
})
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_session_id", nullable = false)
    private ExaminationSession session;

    @Column(name = "doctor_name", length = 120)
    private String doctorName;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "examination_notes", length = 4000)
    private String examinationNotes;

    @Column(name = "diagnosis", length = 1000)
    private String diagnosis;

    @Column(name = "conclusion", length = 2000)
    private String conclusion;

    @Column(name = "treatment_plan", length = 2000)
    private String treatmentPlan;

    @Column(name = "prescription", length = 4000)
    private String prescription;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "signed_at")
    private Instant signedAt;

    protected MedicalRecord() {
    }

    private MedicalRecord(ExaminationSession session, String doctorName, String reason, String examinationNotes,
                          String diagnosis, String conclusion, String treatmentPlan, String prescription,
                          LocalDate followUpDate, Instant signedAt) {
        this.session = session;
        this.doctorName = doctorName;
        this.reason = reason;
        this.examinationNotes = examinationNotes;
        this.diagnosis = diagnosis;
        this.conclusion = conclusion;
        this.treatmentPlan = treatmentPlan;
        this.prescription = prescription;
        this.followUpDate = followUpDate;
        this.signedAt = signedAt;
    }

    public static MedicalRecord draft(ExaminationSession session) {
        return new MedicalRecord(session, null, null, null, null, null, null, null, null, null);
    }

    static MedicalRecord signed(ExaminationSession session, String doctorName, String reason, String examinationNotes,
                                String diagnosis, String conclusion, String treatmentPlan, String prescription,
                                LocalDate followUpDate) {
        return new MedicalRecord(session, doctorName, reason, examinationNotes, diagnosis, conclusion,
                treatmentPlan, prescription, followUpDate, Instant.now());
    }

    @PrePersist
    void onCreate() {
        if (signedAt != null && signedAt.isAfter(Instant.now())) {
            throw new IllegalStateException("Thời điểm ký phiếu không hợp lệ.");
        }
    }

    public UUID getId() { return id; }
    public ExaminationSession getSession() { return session; }
    public String getDoctorName() { return doctorName; }
    public String getReason() { return reason; }
    public String getExaminationNotes() { return examinationNotes; }
    public String getDiagnosis() { return diagnosis; }
    public String getConclusion() { return conclusion; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public String getPrescription() { return prescription; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public Instant getSignedAt() { return signedAt; }
}

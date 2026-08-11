package com.clinicone.examination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "medicalRecord", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("lineNumber asc")
    private List<PrescriptionLine> prescriptionLines = new ArrayList<>();

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_days")
    private Integer followUpDays;

    @Column(name = "follow_up_note", length = 500)
    private String followUpNote;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Version
    @Column(name = "version")
    private long version;

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

    public void saveDraft(String doctorName, String reason, String examinationNotes, String diagnosis,
                          String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate) {
        saveDraft(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription,
                followUpDate, null, null);
    }

    public void saveDraft(String doctorName, String reason, String examinationNotes, String diagnosis,
                          String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                          Integer followUpDays, String followUpNote) {
        if (signedAt != null) {
            throw new IllegalStateException("Phiếu khám đã ký, không thể sửa.");
        }
        this.doctorName = doctorName;
        this.reason = reason;
        this.examinationNotes = examinationNotes;
        this.diagnosis = diagnosis;
        this.conclusion = conclusion;
        this.treatmentPlan = treatmentPlan;
        this.prescription = prescription;
        this.followUpDate = followUpDate;
        this.followUpDays = followUpDays;
        this.followUpNote = followUpNote;
    }

    public void sign(String doctorName, String reason, String examinationNotes, String diagnosis,
                     String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate) {
        sign(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                List.of());
    }

    public void sign(String doctorName, String reason, String examinationNotes, String diagnosis,
                     String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                     List<PrescriptionLine> lines) {
        sign(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription, followUpDate,
                lines, null, null);
    }

    public void sign(String doctorName, String reason, String examinationNotes, String diagnosis,
                     String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                     List<PrescriptionLine> lines, Integer followUpDays, String followUpNote) {
        if (signedAt != null) {
            throw new IllegalStateException("Phiếu khám đã ký, không thể ký lại.");
        }
        saveDraft(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription,
                followUpDate, followUpDays, followUpNote);
        replacePrescriptionLines(lines);
        signedAt = Instant.now();
    }

    public void saveDraft(String doctorName, String reason, String examinationNotes, String diagnosis,
                          String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                          List<PrescriptionLine> lines) {
        saveDraft(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription,
                followUpDate, lines, null, null);
    }

    public void saveDraft(String doctorName, String reason, String examinationNotes, String diagnosis,
                          String conclusion, String treatmentPlan, String prescription, LocalDate followUpDate,
                          List<PrescriptionLine> lines, Integer followUpDays, String followUpNote) {
        saveDraft(doctorName, reason, examinationNotes, diagnosis, conclusion, treatmentPlan, prescription,
                followUpDate, followUpDays, followUpNote);
        replacePrescriptionLines(lines);
    }

    public void replacePrescriptionLines(List<PrescriptionLine> lines) {
        if (signedAt != null) {
            throw new IllegalStateException("Phiếu khám đã ký, không thể sửa.");
        }
        prescriptionLines.clear();
        if (lines != null) {
            prescriptionLines.addAll(lines);
        }
        prescription = null;
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
    public List<PrescriptionLine> getPrescriptionLines() { return List.copyOf(prescriptionLines); }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public Integer getFollowUpDays() { return followUpDays; }
    public String getFollowUpNote() { return followUpNote; }
    public Instant getSignedAt() { return signedAt; }
    public long getVersion() { return version; }
}

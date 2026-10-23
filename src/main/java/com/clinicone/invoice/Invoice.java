package com.clinicone.invoice;

import com.clinicone.appointment.Appointment;
import com.clinicone.examination.ExaminationSession;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoices_code", columnNames = "invoice_code"),
        @UniqueConstraint(name = "uk_invoices_session", columnNames = "session_id")
})
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_code", nullable = false, length = 60)
    private String invoiceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ExaminationSession session;

    @Column(name = "patient_name", nullable = false, length = 120)
    private String patientName;

    @Column(name = "patient_phone", length = 30)
    private String patientPhone;

    @Column(name = "doctor_name", length = 120)
    private String doctorName;

    @Column(name = "room_name", length = 120)
    private String roomName;

    @Column(length = 120)
    private String specialty;

    @Column(name = "exam_fee", nullable = false)
    private long examFee;

    @Column(name = "lab_fee", nullable = false)
    private long labFee;

    @Column(name = "medication_fee", nullable = false)
    private long medicationFee;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceStatus status;

    @Column(name = "cashier_name", length = 120)
    private String cashierName;

    @Column(length = 500)
    private String notes;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
    }

    private Invoice(ExaminationSession session, String invoiceCode, long examFee, String cashierName) {
        this.session = session;
        this.appointment = session.getAppointment();
        this.invoiceCode = invoiceCode;
        var profile = appointment.getPatientProfile();
        var patient = appointment.getPatient();
        this.patientName = profile == null ? patient.getFullName() : profile.getFullName();
        this.patientPhone = profile == null ? patient.getPhone() : profile.getPhone();
        this.doctorName = appointment.getDoctorName();
        this.specialty = appointment.getSpecialty();
        this.examFee = examFee;
        this.labFee = 0;
        this.medicationFee = 0;
        this.discountAmount = 0;
        this.totalAmount = examFee;
        this.paidAmount = 0;
        this.status = InvoiceStatus.PENDING;
        this.cashierName = cashierName;
    }

    public static Invoice create(ExaminationSession session, String invoiceCode, long examFee, String cashierName) {
        if (session == null || session.getAppointment() == null) {
            throw new IllegalArgumentException("Lượt khám không hợp lệ.");
        }
        if (examFee <= 0) {
            throw new IllegalArgumentException("Phí khám phải lớn hơn 0.");
        }
        return new Invoice(session, invoiceCode, examFee, cashierName);
    }

    public void addItem(InvoiceItem item) {
        items.add(item);
        item.attachTo(this);
        recalculateTotal();
    }

    public void recalculateTotal() {
        if (items != null && !items.isEmpty()) {
            long sum = 0;
            long calculatedExamFee = 0;
            long calculatedLabFee = 0;
            long calculatedMedicationFee = 0;
            for (InvoiceItem item : items) {
                sum = Math.addExact(sum, item.getTotalPrice());
                if ("MEDICATION".equalsIgnoreCase(item.getItemType())) {
                    calculatedMedicationFee = Math.addExact(calculatedMedicationFee, item.getTotalPrice());
                } else if ("LAB_TEST".equalsIgnoreCase(item.getItemType()) || "LAB".equalsIgnoreCase(item.getItemType())) {
                    calculatedLabFee = Math.addExact(calculatedLabFee, item.getTotalPrice());
                } else {
                    calculatedExamFee = Math.addExact(calculatedExamFee, item.getTotalPrice());
                }
            }
            this.examFee = calculatedExamFee > 0 ? calculatedExamFee : this.examFee;
            this.labFee = calculatedLabFee;
            this.medicationFee = calculatedMedicationFee;
            this.totalAmount = Math.max(0, sum - discountAmount);
        } else {
            this.totalAmount = Math.max(0, examFee - discountAmount);
        }
    }

    public void markPaid(long amount, PaymentMethod method, String cashier) {
        if (status != InvoiceStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể thanh toán hóa đơn đang chờ.");
        }
        recalculateTotal();
        if (amount != totalAmount || method == null) {
            throw new IllegalStateException("Số tiền không khớp (cần " + totalAmount + ", nhận " + amount + ") hoặc phương thức thanh toán không hợp lệ.");
        }
        paidAmount = amount;
        paymentMethod = method;
        cashierName = cashier;
        paidAt = Instant.now();
        status = InvoiceStatus.PAID;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

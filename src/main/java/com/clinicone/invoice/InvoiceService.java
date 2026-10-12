package com.clinicone.invoice;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.AuthenticatedIds;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.ExaminationSessionStatus;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvoiceService {
    private static final Pattern PUBLIC_INVOICE_CODE = Pattern.compile("^INV-[0-9A-F]{32}$");

    private final InvoiceRepository invoiceRepository;
    private final ExaminationSessionRepository sessionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicServiceRepository clinicServiceRepository;
    private final BusinessLogService businessLogService;

    @Transactional
    public InvoiceResponse createPaymentReceipt(UUID sessionId, String cashierId) {
        ExaminationSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(this::sessionNotFound);

        return invoiceRepository.findBySession_Id(sessionId)
                .map(InvoiceResponse::from)
                .orElseGet(() -> createReceipt(session, cashierId));
    }

    @Transactional
    public InvoiceResponse createPaymentReceiptForAppointment(String appointmentCode, String cashierId) {
        String code = normalizeCode(appointmentCode);
        ExaminationSession session = sessionRepository.findByAppointment_CodeForUpdate(code)
                .orElseThrow(this::sessionNotFound);
        return invoiceRepository.findBySession_Id(session.getId())
                .map(InvoiceResponse::from)
                .orElseGet(() -> createReceipt(session, cashierId));
    }

    @Transactional
    public InvoiceResponse markPaid(String invoiceCode, MarkInvoicePaidRequest request, String cashierId) {
        if (request == null || request.amount() == null || request.paymentMethod() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PAYMENT_REQUIRED", "Cần nhập đủ thông tin thanh toán.");
        }
        Invoice invoice = invoiceRepository.findByInvoiceCodeForUpdate(normalizeCode(invoiceCode))
                .orElseThrow(this::invoiceNotFound);
        InvoiceStatus previousStatus = invoice.getStatus();
        try {
            invoice.markPaid(request.amount(), request.paymentMethod(), cashierId);
        } catch (IllegalStateException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_INVALID", exception.getMessage());
        }
        Invoice saved = invoiceRepository.save(invoice);
        if (saved.getId() != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "INVOICE", saved.getId(),
                    previousStatus.name(), saved.getStatus().name(), "INVOICE_PAYMENT_RECORDED", cashierId,
                    "Phương thức: " + request.paymentMethod() + "; số tiền: " + request.amount());
        }
        return InvoiceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForPatient(String accountId) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        return invoiceRepository.findByAppointment_Patient_IdOrderByCreatedAtDesc(patientId)
                .stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse lookupForPatient(String accountId, String invoiceCode) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        return invoiceRepository.findByInvoiceCodeAndAppointment_Patient_Id(normalizeCode(invoiceCode), patientId)
                .map(InvoiceResponse::from)
                .orElseThrow(this::invoiceNotFound);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse lookupForPatientByAppointment(String accountId, String appointmentCode) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        return invoiceRepository.findByAppointment_AppointmentCodeAndAppointment_Patient_Id(
                        normalizeCode(appointmentCode), patientId)
                .map(InvoiceResponse::from)
                .orElseThrow(this::invoiceNotFound);
    }

    @Transactional(readOnly = true)
    public PublicInvoiceResponse lookupPublicInvoice(String invoiceCode) {
        String code = normalizeCode(invoiceCode);
        if (!PUBLIC_INVOICE_CODE.matcher(code).matches()) {
            throw invoiceNotFound();
        }
        return invoiceRepository.findByInvoiceCode(code)
                .map(PublicInvoiceResponse::from)
                .orElseThrow(this::invoiceNotFound);
    }

    private InvoiceResponse createReceipt(ExaminationSession session, String cashierId) {
        if (session.getStatus() != ExaminationSessionStatus.COMPLETED) {
            throw new AuthException(HttpStatus.CONFLICT, "EXAMINATION_NOT_COMPLETED",
                    "Chỉ tạo phiếu thanh toán sau khi bác sĩ hoàn tất lượt khám.");
        }
        var medicalRecord = medicalRecordRepository.findBySession_Id(session.getId())
                .filter(record -> record.getSignedAt() != null)
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "SIGNED_RECORD_REQUIRED",
                        "Lượt khám chưa có phiếu khám đã ký."));
        var appointment = session.getAppointment();
        if (appointment.getServiceId() == null) {
            throw new AuthException(HttpStatus.CONFLICT, "SERVICE_TARIFF_REQUIRED",
                    "Lịch khám chưa được gắn dịch vụ có biểu phí.");
        }
        ClinicService clinicService = clinicServiceRepository.findById(appointment.getServiceId())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "SERVICE_TARIFF_REQUIRED",
                        "Không tìm thấy dịch vụ khám để tính phí."));
        long fee = clinicService.getConsultationFee();
        if (fee <= 0) {
            throw new AuthException(HttpStatus.CONFLICT, "SERVICE_TARIFF_NOT_CONFIGURED",
                    "Dịch vụ khám chưa được cấu hình phí.");
        }
        Invoice invoice = Invoice.create(session, nextInvoiceCode(), fee, cashierId);
        String itemName = StringUtils.hasText(appointment.getServiceName())
                ? appointment.getServiceName() : clinicService.getName();
        invoice.addItem(InvoiceItem.consultation(itemName, fee));
        Invoice saved = invoiceRepository.save(invoice);
        if (saved.getId() != null) {
            businessLogService.recordActivity(UUID.randomUUID(), "INVOICE", saved.getId(), null,
                    InvoiceStatus.PENDING.name(), "PAYMENT_RECEIPT_CREATED", cashierId,
                    "Lịch hẹn: " + appointment.getAppointmentCode());
        }
        return InvoiceResponse.from(saved);
    }

    private String nextInvoiceCode() {
        return "INV-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String normalizeCode(String invoiceCode) {
        if (!StringUtils.hasText(invoiceCode) || invoiceCode.trim().length() > 60) {
            throw invoiceNotFound();
        }
        return invoiceCode.trim().toUpperCase();
    }

    private AuthException sessionNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "EXAMINATION_SESSION_NOT_FOUND", "Không tìm thấy lượt khám.");
    }

    private AuthException invoiceNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Không tìm thấy phiếu thanh toán.");
    }
}

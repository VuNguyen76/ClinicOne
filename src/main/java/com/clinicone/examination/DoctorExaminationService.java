package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketStatus;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.audit.BusinessLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DoctorExaminationService {
    private final QueueTicketRepository ticketRepository;
    private final ExaminationSessionRepository sessionRepository;
    private final MedicalRecordRepository recordRepository;
    private final StaffAccountRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientNotificationService notificationService;
    private final BusinessLogService businessLogService;

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, null, null);
    }

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, notificationService, null);
    }

    @Autowired
    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService,
                                    BusinessLogService businessLogService) {
        this.ticketRepository = ticketRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.notificationService = notificationService;
        this.businessLogService = businessLogService;
    }

    @Transactional
    public DoctorExaminationResponse open(UUID ticketId, String staffId) {
        Workspace workspace = workspace(ticketId, staffId);
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        workspace.session().begin();
        MedicalRecord record = record(workspace.session());
        if (record.getDoctorName() == null || record.getDoctorName().isBlank()) {
            record.saveDraft(doctorName(staffId, workspace.appointment()), record.getReason(),
                    record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(),
                    record.getTreatmentPlan(), record.getPrescription(), record.getFollowUpDate());
        }
        recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                workspace.session().getStatus().name(), "START_EXAMINATION", staffId, null);
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse saveDraft(UUID ticketId, String staffId, DoctorExaminationRequest request) {
        Workspace workspace = workspace(ticketId, staffId);
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        workspace.session().begin();
        MedicalRecord record = record(workspace.session());
        try {
            record.saveDraft(doctorName(staffId, workspace.appointment()), request.reason(), request.examinationNotes(),
                    request.diagnosis(), request.conclusion(), request.treatmentPlan(), request.prescription(),
                    request.followUpDate());
        } catch (IllegalStateException exception) {
            throw conflict("MEDICAL_RECORD_LOCKED", exception.getMessage());
        }
        recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                workspace.session().getStatus().name(), "START_EXAMINATION", staffId, null);
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse sign(UUID ticketId, String staffId, DoctorExaminationRequest request) {
        Workspace workspace = workspace(ticketId, staffId);
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        String previousTicketStatus = workspace.ticket().getStatus().name();
        String previousAppointmentStatus = workspace.appointment().getStatus().name();
        workspace.session().begin();
        requireRequiredFields(request);
        MedicalRecord record = record(workspace.session());
        try {
            record.sign(doctorName(staffId, workspace.appointment()), request.reason(), request.examinationNotes(),
                    request.diagnosis(), request.conclusion(), request.treatmentPlan(), request.prescription(),
                    request.followUpDate());
            workspace.session().complete();
            workspace.ticket().complete();
            workspace.appointment().complete();
            appointmentRepository.save(workspace.appointment());
            ticketRepository.save(workspace.ticket());
            sessionRepository.save(workspace.session());
            recordRepository.save(record);
            recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                    workspace.session().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            recordTransition(eventId, "QUEUE_TICKET", workspace.ticket().getId(), previousTicketStatus,
                    workspace.ticket().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            recordTransition(eventId, "APPOINTMENT", workspace.appointment().getId(), previousAppointmentStatus,
                    workspace.appointment().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            if (notificationService != null) {
                notificationService.notifyMedicalRecordSigned(workspace.appointment().getPatient().getId(), record.getId(),
                        workspace.appointment().getAppointmentCode(), record.getDoctorName(), workspace.appointment().getSpecialty());
            }
        } catch (IllegalStateException exception) {
            throw conflict("MEDICAL_RECORD_SIGN_FAILED", exception.getMessage());
        }
        return response(workspace.ticket(), workspace.session(), record);
    }

    private Workspace workspace(UUID ticketId, String staffId) {
        QueueTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "QUEUE_TICKET_NOT_FOUND",
                        "Không tìm thấy lượt trong hàng đợi."));
        if (ticket.getStatus() != QueueTicketStatus.IN_SERVICE) {
            throw conflict("QUEUE_INVALID_STATE", "Lượt khám chưa ở trạng thái đang khám.");
        }
        UUID doctorId = parseStaffId(staffId);
        if (!doctorId.equals(ticket.getAppointment().getDoctorStaffId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_TICKET_SCOPE",
                    "Bác sĩ chỉ được mở phiếu của lượt đã được phân công.");
        }
        doctorProfileRepository.findByStaffAccount_Id(doctorId)
                .filter(DoctorProfile::isActive)
                .filter(profile -> profile.getRoom().getCode().equalsIgnoreCase(ticket.getRoom().getCode()))
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ASSIGNMENT_REQUIRED",
                        "Bác sĩ chưa được gán đúng chuyên khoa và phòng khám."));
        return new Workspace(ticket, ticket.getAppointment(), session(ticket.getAppointment()));
    }

    private UUID parseStaffId(String staffId) {
        try {
            return UUID.fromString(staffId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập bác sĩ không hợp lệ.");
        }
    }

    private ExaminationSession session(Appointment appointment) {
        return sessionRepository.findByAppointment_Id(appointment.getId())
                .orElseGet(() -> sessionRepository.save(ExaminationSession.create(appointment)));
    }

    private MedicalRecord record(ExaminationSession session) {
        return recordRepository.findBySession_Id(session.getId())
                .orElseGet(() -> recordRepository.save(MedicalRecord.draft(session)));
    }

    private String doctorName(String staffId, Appointment appointment) {
        try {
            return staffRepository.findById(UUID.fromString(staffId))
                    .map(StaffAccount::getFullName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(appointment.getDoctorName());
        } catch (IllegalArgumentException exception) {
            return appointment.getDoctorName();
        }
    }

    private void requireRequiredFields(DoctorExaminationRequest request) {
        if (blank(request.reason()) || blank(request.examinationNotes()) || blank(request.diagnosis())
                || blank(request.conclusion())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_RECORD_REQUIRED",
                    "Cần nhập lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký phiếu.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private void recordTransition(UUID eventId, String entityType, UUID entityId, String previousStatus,
                                  String nextStatus, String eventType, String actor, String reason) {
        if (businessLogService != null && entityId != null) {
            businessLogService.recordTransition(eventId, entityType, entityId, previousStatus, nextStatus,
                    eventType, actor, reason);
        }
    }

    private DoctorExaminationResponse response(QueueTicket ticket, ExaminationSession session, MedicalRecord record) {
        Appointment appointment = ticket.getAppointment();
        var patient = appointment.getPatient();
        return new DoctorExaminationResponse(ticket.getId(), appointment.getId(), session.getId(), ticket.getQueueNumber(),
                ticket.getRoom().getName(), appointment.getAppointmentCode(), appointment.getSpecialty(),
                record.getDoctorName() == null ? appointment.getDoctorName() : record.getDoctorName(),
                appointment.getAppointmentDate(), appointment.getStartTime(), patient.getFullName(),
                patient.getDateOfBirth(), patient.getGender(), patient.getPhone(), record.getReason(),
                record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(), record.getTreatmentPlan(),
                record.getPrescription(), record.getFollowUpDate(), session.getStatus().name(), record.getSignedAt());
    }

    private record Workspace(QueueTicket ticket, Appointment appointment, ExaminationSession session) {
    }
}

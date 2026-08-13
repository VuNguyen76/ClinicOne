package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketStatus;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.medication.Medication;
import com.clinicone.medication.MedicationCatalogService;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
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
    private final MedicationCatalogService medicationCatalogService;
    private final GeneratedClinicSlotRepository generatedSlotRepository;
    private final WrongProfileIncidentRepository wrongProfileIncidentRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final Clock clock;

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, null, null, null, null, null, null, Clock.systemUTC());
    }

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, notificationService, null, null, null, null, null, Clock.systemUTC());
    }

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService,
                                    GeneratedClinicSlotRepository generatedSlotRepository) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, notificationService, null, null, generatedSlotRepository, null, null,
                Clock.systemUTC());
    }

    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService,
                                    GeneratedClinicSlotRepository generatedSlotRepository,
                                    WrongProfileIncidentRepository wrongProfileIncidentRepository) {
        this(ticketRepository, sessionRepository, recordRepository, staffRepository, appointmentRepository,
                doctorProfileRepository, notificationService, null, null, generatedSlotRepository,
                wrongProfileIncidentRepository, null, Clock.systemUTC());
    }

    @Autowired
    public DoctorExaminationService(QueueTicketRepository ticketRepository,
                                    ExaminationSessionRepository sessionRepository,
                                    MedicalRecordRepository recordRepository,
                                    StaffAccountRepository staffRepository,
                                    AppointmentRepository appointmentRepository,
                                    DoctorProfileRepository doctorProfileRepository,
                                    PatientNotificationService notificationService,
                                    BusinessLogService businessLogService,
                                    MedicationCatalogService medicationCatalogService,
                                    GeneratedClinicSlotRepository generatedSlotRepository,
                                    WrongProfileIncidentRepository wrongProfileIncidentRepository,
                                    DoctorScheduleRepository doctorScheduleRepository,
                                    Clock clock) {
        this.ticketRepository = ticketRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.notificationService = notificationService;
        this.businessLogService = businessLogService;
        this.medicationCatalogService = medicationCatalogService;
        this.generatedSlotRepository = generatedSlotRepository;
        this.wrongProfileIncidentRepository = wrongProfileIncidentRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional(readOnly = true)
    public DoctorExaminationResponse open(UUID ticketId, String staffId) {
        Workspace workspace = workspace(ticketId, staffId);
        MedicalRecord record = workspace.appointment().requiresMedicalRecord()
                ? recordRepository.findBySession_Id(workspace.session().getId()).orElse(null)
                : null;
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse start(UUID ticketId, String staffId, String requestKey) {
        String normalizedRequestKey = normalizeRequiredRequestKey(requestKey);
        QueueTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "QUEUE_TICKET_NOT_FOUND",
                        "Không tìm thấy lượt trong hàng đợi."));
        UUID doctorId = lockDoctorAssignment(ticket, staffId);
        ensureDoctorHasActiveShift(ticket, doctorId);
        ExaminationSession session = sessionRepository.findByAppointment_IdForUpdate(ticket.getAppointment().getId())
                .orElseThrow(() -> conflict("EXAMINATION_NOT_CREATED", "Lượt khám chưa được tạo từ lần check-in."));

        sessionRepository.findByStartRequestKey(normalizedRequestKey)
                .filter(existing -> !existing.getId().equals(session.getId()))
                .ifPresent(existing -> {
                    throw conflict("IDEMPOTENCY_CONFLICT", "Mã chống gửi lặp đã được dùng cho lượt khám khác.");
                });
        if (ticket.getStatus() == QueueTicketStatus.IN_SERVICE
                && session.getStatus() == ExaminationSessionStatus.IN_PROGRESS
                && normalizedRequestKey.equals(session.getStartRequestKey())) {
            MedicalRecord existingRecord = ticket.getAppointment().requiresMedicalRecord()
                    ? recordRepository.findBySession_Id(session.getId()).orElse(null)
                    : null;
            return response(ticket, session, existingRecord);
        }
        if (ticket.getStatus() != QueueTicketStatus.CALLED || session.getStatus() != ExaminationSessionStatus.SCHEDULED) {
            throw conflict("QUEUE_INVALID_STATE", "Chỉ có thể bắt đầu khi bệnh nhân đang được gọi và chưa bắt đầu khám.");
        }
        if (ticketRepository.countInServiceForDoctorExcludingTicket(doctorId, ticketId) > 0) {
            throw conflict("DOCTOR_ACTIVE_EXAMINATION", "Bác sĩ đang có một lượt khám khác chưa hoàn thành.");
        }

        UUID eventId = UUID.randomUUID();
        String previousTicketStatus = ticket.getStatus().name();
        String previousSessionStatus = session.getStatus().name();
        ticket.startService();
        session.begin();
        session.assignStartRequestKey(normalizedRequestKey);
        ticketRepository.save(ticket);
        sessionRepository.save(session);

        MedicalRecord record = null;
        if (ticket.getAppointment().requiresMedicalRecord()) {
            record = record(session);
            if (record.getDoctorName() == null || record.getDoctorName().isBlank()) {
                record.saveDraft(doctorName(staffId, ticket.getAppointment()), record.getReason(),
                        record.getExaminationNotes(), record.getDiagnosis(), record.getConclusion(),
                        record.getTreatmentPlan(), record.getPrescription(), record.getFollowUpDate());
            }
        }
        recordTransition(eventId, "QUEUE_TICKET", ticket.getId(), previousTicketStatus,
                ticket.getStatus().name(), "START_EXAMINATION", staffId, null);
        recordTransition(eventId, "EXAMINATION", session.getId(), previousSessionStatus,
                session.getStatus().name(), "START_EXAMINATION", staffId, null);
        return response(ticket, session, record);
    }

    @Transactional
    public DoctorExaminationResponse saveDraft(UUID ticketId, String staffId, DoctorExaminationRequest request) {
        Workspace workspace = workspace(ticketId, staffId);
        if (!workspace.appointment().requiresMedicalRecord()) {
            throw conflict("MEDICAL_RECORD_NOT_REQUIRED", "Loại lượt khám này không dùng phiếu khám.");
        }
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        workspace.session().begin();
        MedicalRecord record = record(workspace.session());
        requireCurrentRecordVersion(record, request);
        try {
            List<PrescriptionLine> prescriptionLines = prescriptionLines(record, request);
            record.saveDraft(doctorName(staffId, workspace.appointment()), request.reason(), request.examinationNotes(),
                    request.diagnosis(), request.conclusion(), request.treatmentPlan(), request.prescription(),
                    request.followUpDate(), prescriptionLines, request.followUpDays(), normalizeFollowUpNote(request.followUpNote()));
            recordRepository.saveAndFlush(record);
        } catch (IllegalStateException exception) {
            throw conflict("MEDICAL_RECORD_LOCKED", exception.getMessage());
        }
        recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                workspace.session().getStatus().name(), "START_EXAMINATION", staffId, null);
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse sign(UUID ticketId, String staffId, DoctorExaminationRequest request,
                                          String requestKey) {
        String normalizedRequestKey = normalizeSigningRequestKey(requestKey);
        Workspace workspace = workspace(ticketId, staffId, true);
        MedicalRecord existingRecord = workspace.appointment().requiresMedicalRecord()
                ? recordRepository.findBySession_Id(workspace.session().getId()).orElse(null)
                : null;
        if (workspace.ticket().getStatus() == QueueTicketStatus.COMPLETED
                && (!workspace.appointment().requiresMedicalRecord()
                || (existingRecord != null && existingRecord.getSignedAt() != null))) {
            if (normalizedRequestKey.equals(workspace.session().getSignRequestKey())) {
                return response(workspace.ticket(), workspace.session(), existingRecord);
            }
            throw conflict("EXAMINATION_ALREADY_COMPLETED", "Lượt khám đã hoàn tất và phiếu khám đang ở chế độ chỉ đọc.");
        }
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        String previousTicketStatus = workspace.ticket().getStatus().name();
        String previousAppointmentStatus = workspace.appointment().getStatus().name();
        workspace.session().begin();
        if (!workspace.appointment().requiresMedicalRecord()) {
            markAppointmentSlotUnavailable(workspace.appointment());
            workspace.session().assignSignRequestKey(normalizedRequestKey);
            workspace.session().complete();
            workspace.ticket().complete();
            workspace.appointment().complete();
            appointmentRepository.save(workspace.appointment());
            ticketRepository.save(workspace.ticket());
            sessionRepository.save(workspace.session());
            recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                    workspace.session().getStatus().name(), "COMPLETE_EXAMINATION", staffId, null);
            recordTransition(eventId, "QUEUE_TICKET", workspace.ticket().getId(), previousTicketStatus,
                    workspace.ticket().getStatus().name(), "COMPLETE_EXAMINATION", staffId, null);
            recordTransition(eventId, "APPOINTMENT", workspace.appointment().getId(), previousAppointmentStatus,
                    workspace.appointment().getStatus().name(), "COMPLETE_EXAMINATION", staffId, null);
            return response(workspace.ticket(), workspace.session(), null);
        }
        requireRequiredFields(request);
        validateFollowUpForSigning(request);
        MedicalRecord record = existingRecord == null ? record(workspace.session()) : existingRecord;
        requireCurrentRecordVersion(record, request);
        try {
            List<PrescriptionLine> prescriptionLines = prescriptionLines(record, request);
            record.sign(doctorName(staffId, workspace.appointment()), request.reason(), request.examinationNotes(),
                    request.diagnosis(), request.conclusion(), request.treatmentPlan(), request.prescription(),
                    request.followUpDate(), prescriptionLines, request.followUpDays(), normalizeFollowUpNote(request.followUpNote()));
            markAppointmentSlotUnavailable(workspace.appointment());
            workspace.session().assignSignRequestKey(normalizedRequestKey);
            workspace.session().complete();
            workspace.ticket().complete();
            workspace.appointment().complete();
            appointmentRepository.save(workspace.appointment());
            ticketRepository.save(workspace.ticket());
            sessionRepository.save(workspace.session());
            recordRepository.saveAndFlush(record);
            recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                    workspace.session().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            recordTransition(eventId, "QUEUE_TICKET", workspace.ticket().getId(), previousTicketStatus,
                    workspace.ticket().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            recordTransition(eventId, "APPOINTMENT", workspace.appointment().getId(), previousAppointmentStatus,
                    workspace.appointment().getStatus().name(), "SIGN_MEDICAL_RECORD", staffId, null);
            if (notificationService != null && workspace.appointment().getPatient() != null) {
                try {
                    notificationService.notifyMedicalRecordSigned(workspace.appointment().getPatient().getId(), record.getId(),
                            workspace.appointment().getAppointmentCode(), record.getDoctorName(), workspace.appointment().getSpecialty());
                } catch (RuntimeException ignored) {
                    // Notification delivery must not compromise the clinical transaction.
                }
            }
        } catch (IllegalStateException exception) {
            throw conflict("MEDICAL_RECORD_SIGN_FAILED", exception.getMessage());
        }
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse stop(UUID ticketId, String staffId, StopExaminationRequest request) {
        Workspace workspace = workspace(ticketId, staffId);
        String reason = normalizeStopReason(request == null ? null : request.reason());
        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        String previousTicketStatus = workspace.ticket().getStatus().name();
        String previousAppointmentStatus = workspace.appointment().getStatus().name();
        try {
            markAppointmentSlotUnavailable(workspace.appointment());
            workspace.session().stop();
            workspace.ticket().stopService(reason);
            workspace.appointment().markNotPerformed();
            appointmentRepository.save(workspace.appointment());
            ticketRepository.save(workspace.ticket());
            sessionRepository.save(workspace.session());
        } catch (IllegalStateException exception) {
            throw conflict("EXAMINATION_STOP_NOT_ALLOWED", exception.getMessage());
        }
        recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                workspace.session().getStatus().name(), "STOP_EXAMINATION", staffId, reason);
        recordTransition(eventId, "QUEUE_TICKET", workspace.ticket().getId(), previousTicketStatus,
                workspace.ticket().getStatus().name(), "STOP_EXAMINATION", staffId, reason);
        recordTransition(eventId, "APPOINTMENT", workspace.appointment().getId(), previousAppointmentStatus,
                workspace.appointment().getStatus().name(), "STOP_EXAMINATION", staffId, reason);
        MedicalRecord record = workspace.appointment().requiresMedicalRecord()
                ? recordRepository.findBySession_Id(workspace.session().getId()).orElse(null)
                : null;
        return response(workspace.ticket(), workspace.session(), record);
    }

    @Transactional
    public DoctorExaminationResponse wrongProfile(UUID ticketId, String staffId, WrongProfileRequest request) {
        Workspace workspace = workspace(ticketId, staffId);
        UUID doctorId = parseStaffId(staffId);
        String reason = normalizeWrongProfileReason(request == null ? null : request.reason());
        MedicalRecord record = workspace.appointment().requiresMedicalRecord()
                ? recordRepository.findBySession_Id(workspace.session().getId()).orElse(null)
                : null;
        if (wrongProfileIncidentRepository == null) {
            throw new IllegalStateException("Kho lưu đối soát nhầm hồ sơ chưa sẵn sàng.");
        }

        UUID eventId = UUID.randomUUID();
        String previousSessionStatus = workspace.session().getStatus().name();
        String previousTicketStatus = workspace.ticket().getStatus().name();
        Instant previousStartedAt = workspace.session().getStartedAt();
        wrongProfileIncidentRepository.save(WrongProfileIncident.seal(record, workspace.ticket(), workspace.session(),
                doctorId, reason));
        if (record != null) {
            recordRepository.delete(record);
            recordRepository.flush();
        }
        try {
            workspace.session().resetForWrongProfile();
            workspace.ticket().returnForCorrectProfile();
            sessionRepository.save(workspace.session());
            ticketRepository.save(workspace.ticket());
        } catch (IllegalStateException exception) {
            throw conflict("WRONG_PROFILE_NOT_ALLOWED", exception.getMessage());
        }
        String logReason = wrongProfileLogReason(reason, previousStartedAt);
        recordTransition(eventId, "EXAMINATION", workspace.session().getId(), previousSessionStatus,
                workspace.session().getStatus().name(), "WRONG_PATIENT_PROFILE", staffId, logReason);
        recordTransition(eventId, "QUEUE_TICKET", workspace.ticket().getId(), previousTicketStatus,
                workspace.ticket().getStatus().name(), "WRONG_PATIENT_PROFILE", staffId, logReason);
        return response(workspace.ticket(), workspace.session(), null);
    }

    private Workspace workspace(UUID ticketId, String staffId) {
        return workspace(ticketId, staffId, false);
    }

    private Workspace workspace(UUID ticketId, String staffId, boolean allowSignedCompletion) {
        QueueTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "QUEUE_TICKET_NOT_FOUND",
                        "Không tìm thấy lượt trong hàng đợi."));
        ensureDoctorAssignment(ticket, staffId);
        ExaminationSession session = sessionRepository.findByAppointment_Id(ticket.getAppointment().getId())
                .orElseThrow(() -> conflict("EXAMINATION_NOT_CREATED", "Lượt khám chưa được tạo từ lần check-in."));
        if (ticket.getStatus() == QueueTicketStatus.IN_SERVICE
                && session.getStatus() == ExaminationSessionStatus.IN_PROGRESS) {
            return new Workspace(ticket, ticket.getAppointment(), session);
        }
        if (allowSignedCompletion && ticket.getStatus() == QueueTicketStatus.COMPLETED
                && session.getStatus() == ExaminationSessionStatus.COMPLETED) {
            if (!ticket.getAppointment().requiresMedicalRecord()
                    || recordRepository.findBySession_Id(session.getId()).map(MedicalRecord::getSignedAt).isPresent()) {
                return new Workspace(ticket, ticket.getAppointment(), session);
            }
        }
        throw conflict("QUEUE_INVALID_STATE", "Lượt khám chưa ở trạng thái đang khám.");
    }

    private UUID parseStaffId(String staffId) {
        try {
            return UUID.fromString(staffId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập bác sĩ không hợp lệ.");
        }
    }

    private UUID ensureDoctorAssignment(QueueTicket ticket, String staffId) {
        return validateDoctorAssignment(ticket, parseStaffId(staffId), false);
    }

    private UUID lockDoctorAssignment(QueueTicket ticket, String staffId) {
        return validateDoctorAssignment(ticket, parseStaffId(staffId), true);
    }

    private UUID validateDoctorAssignment(QueueTicket ticket, UUID doctorId, boolean lockDoctor) {
        if (!doctorId.equals(ticket.getEffectiveDoctorStaffId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_TICKET_SCOPE",
                    "Bác sĩ chỉ được mở phiếu của lượt đã được phân công.");
        }
        DoctorProfile profile = (lockDoctor ? doctorProfileRepository.findByStaffAccount_IdForUpdate(doctorId)
                : doctorProfileRepository.findByStaffAccount_Id(doctorId))
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ASSIGNMENT_REQUIRED",
                        "Bác sĩ chưa được gán đúng chuyên khoa và phòng khám."));
        if (profile.getStaffAccount().getStatus() == AccountStatus.LOCKED) {
            throw new AuthException(HttpStatus.FORBIDDEN, "STAFF_ACCOUNT_LOCKED",
                    "Tài khoản nhân viên đã bị khóa và không thể thực hiện lượt khám.");
        }
        if (!profile.isActive()
                || !profile.getRoom().getCode().equalsIgnoreCase(ticket.getRoom().getCode())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ASSIGNMENT_REQUIRED",
                    "Bác sĩ chưa được gán đúng chuyên khoa và phòng khám.");
        }
        return doctorId;
    }

    private void ensureDoctorHasActiveShift(QueueTicket ticket, UUID doctorId) {
        if (doctorScheduleRepository == null) {
            return;
        }
        ZoneId clinicZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(clock.withZone(clinicZone));
        if (!today.equals(ticket.getAppointment().getAppointmentDate())) {
            throw conflict("DOCTOR_SHIFT_INACTIVE", "Bác sĩ không có ca làm việc đang hiệu lực cho ngày khám.");
        }
        var now = LocalTime.now(clock.withZone(clinicZone));
        DoctorProfile profile = doctorProfileRepository.findByStaffAccount_Id(doctorId)
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> conflict("DOCTOR_ASSIGNMENT_REQUIRED",
                        "Bác sĩ chưa được gán đúng chuyên khoa và phòng khám."));
        long activeSchedules = doctorScheduleRepository
                .findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(profile.getId(), today.getDayOfWeek()).stream()
                .filter(schedule -> !now.isBefore(schedule.getStartTime())
                        && now.isBefore(schedule.getEndTime()))
                .count();
        if (activeSchedules != 1) {
            throw conflict("DOCTOR_SHIFT_INACTIVE", "Bác sĩ không có đúng một ca làm việc đang hiệu lực.");
        }
    }

    private String normalizeRequiredRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Cần mã chống gửi lặp để bắt đầu khám.");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 80) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Mã chống gửi lặp không được dài quá 80 ký tự.");
        }
        return normalized;
    }

    private String normalizeSigningRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Cần mã chống gửi lặp để ký phiếu khám.");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 80) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Mã chống gửi lặp không được dài quá 80 ký tự.");
        }
        return normalized;
    }

    private String normalizeStopReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EXAMINATION_STOP_REASON_INVALID",
                    "Lý do dừng lượt khám phải từ 10 đến 500 ký tự.");
        }
        return normalized;
    }

    private String normalizeWrongProfileReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "WRONG_PROFILE_REASON_INVALID",
                    "Lý do bắt đầu nhầm hồ sơ phải từ 10 đến 500 ký tự.");
        }
        return normalized;
    }

    private String wrongProfileLogReason(String reason, Instant startedAt) {
        String prefix = "Bắt đầu nhầm hồ sơ" + (startedAt == null ? ". " : " lúc " + startedAt + ". ");
        return prefix + reason.substring(0, Math.min(reason.length(), 500 - prefix.length()));
    }

    private void markAppointmentSlotUnavailable(Appointment appointment) {
        if (appointment.getServiceId() == null || generatedSlotRepository == null) {
            return;
        }
        GeneratedClinicSlot slot = generatedSlotRepository
                .findFirstByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(appointment.getDoctorStaffId(),
                        appointment.getAppointmentDate(), appointment.getStartTime(), GeneratedSlotStatus.OPEN)
                .orElseThrow(() -> conflict("APPOINTMENT_SLOT_NOT_FOUND",
                        "Không tìm thấy khung giờ đang hoạt động của lịch hẹn."));
        slot.cancel();
        generatedSlotRepository.save(slot);
    }

    private MedicalRecord record(ExaminationSession session) {
        return recordRepository.findBySession_Id(session.getId())
                .orElseGet(() -> recordRepository.save(MedicalRecord.draft(session)));
    }

    private List<PrescriptionLine> prescriptionLines(MedicalRecord record, DoctorExaminationRequest request) {
        if (request.prescription() != null && !request.prescription().isBlank()) {
            throw conflict("PRESCRIPTION_LEGACY_FORMAT_NOT_ALLOWED",
                    "Hãy nhập thuốc theo từng dòng gồm liều, số lượng và cách dùng.");
        }
        List<PrescriptionLineRequest> items = request.prescriptionLines();
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() > 20) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PRESCRIPTION_LINE_LIMIT",
                    "Một đơn thuốc có tối đa 20 dòng.");
        }
        ArrayList<PrescriptionLine> lines = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            PrescriptionLineRequest item = items.get(index);
            if (item == null || blank(item.medicationName()) || item.medicationName().trim().length() > 200
                    || blank(item.dosage()) || item.dosage().trim().length() > 100
                    || item.quantity() == null || item.quantity() < 1 || item.quantity() > 999
                    || blank(item.instructions()) || item.instructions().trim().length() > 500) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "PRESCRIPTION_LINE_INVALID",
                        "Mỗi dòng thuốc cần có tên, liều, số lượng và cách dùng hợp lệ.");
            }
            if (item.medicationId() == null) {
                lines.add(PrescriptionLine.create(record, item, index + 1));
                continue;
            }
            if (medicationCatalogService == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICATION_NOT_AVAILABLE",
                        "Thuốc được chọn không còn trong danh mục đang sử dụng.");
            }
            Medication medication = medicationCatalogService.requireActive(item.medicationId());
            lines.add(PrescriptionLine.create(record, medication.getId(), medication.getName(), item.dosage(),
                    item.quantity(), item.instructions(), index + 1));
        }
        return List.copyOf(lines);
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

    private void validateFollowUpForSigning(DoctorExaminationRequest request) {
        String followUpNote = normalizeFollowUpNote(request.followUpNote());
        Integer followUpDays = request.followUpDays();
        if (followUpDays == null) {
            if (followUpNote != null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "FOLLOW_UP_DAYS_REQUIRED",
                        "Số ngày tái khám là bắt buộc khi có ghi chú tái khám.");
            }
            return;
        }
        if (followUpDays < 1 || followUpDays > 365) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "FOLLOW_UP_DAYS_INVALID",
                    "Số ngày tái khám phải từ 1 đến 365.");
        }
    }

    private String normalizeFollowUpNote(String value) {
        return blank(value) ? null : value.trim();
    }

    private void requireCurrentRecordVersion(MedicalRecord record, DoctorExaminationRequest request) {
        if (request.recordVersion() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_RECORD_VERSION_REQUIRED",
                    "Thiếu phiên bản phiếu khám. Hãy tải lại trang trước khi lưu.");
        }
        if (request.recordVersion() != record.getVersion()) {
            throw conflict("MEDICAL_RECORD_VERSION_CONFLICT",
                    "Phiếu khám đã được cập nhật ở một cửa sổ khác. Hãy tải lại trang trước khi tiếp tục.");
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
        var profile = appointment.getPatientProfile();
        boolean requiresRecord = appointment.requiresMedicalRecord();
        String recordDoctorName = record == null ? null : record.getDoctorName();
        List<MedicalRecordResponse> history = patient == null ? List.of() : recordRepository
                .findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(patient.getId())
                .stream().map(MedicalRecordResponse::from).toList();
        String patientName = patient == null && profile == null ? null
                : patient == null ? profile.getFullName() : patient.getFullName();
        LocalDate patientDateOfBirth = patient == null && profile == null ? null
                : patient == null ? profile.getDateOfBirth() : patient.getDateOfBirth();
        String patientGender = patient == null && profile == null ? null
                : patient == null ? profile.getGender() : patient.getGender();
        String patientPhone = patient == null && profile == null ? null
                : patient == null ? profile.getPhone() : patient.getPhone();
        return new DoctorExaminationResponse(ticket.getId(), appointment.getId(), session.getId(), ticket.getQueueNumber(),
                ticket.getRoom().getName(), appointment.getAppointmentCode(), appointment.getSpecialty(),
                recordDoctorName == null ? ticket.getEffectiveDoctorName() : recordDoctorName,
                appointment.getAppointmentDate(), appointment.getStartTime(), patientName,
                patientDateOfBirth, patientGender, patientPhone, record == null ? null : record.getReason(),
                record == null ? null : record.getExaminationNotes(), record == null ? null : record.getDiagnosis(),
                record == null ? null : record.getConclusion(), record == null ? null : record.getTreatmentPlan(),
                record == null ? null : record.getPrescription(), record == null ? null : record.getFollowUpDate(),
                record == null ? null : record.getFollowUpDays(), record == null ? null : record.getFollowUpNote(),
                session.getStatus().name(), record == null ? null : record.getSignedAt(),
                record == null ? null : record.getDraftSavedAt(), record == null ? null : record.getVersion(), requiresRecord, history,
                record == null ? List.of() : record.getPrescriptionLines().stream().map(PrescriptionLineResponse::from).toList());
    }

    private record Workspace(QueueTicket ticket, Appointment appointment, ExaminationSession session) {
    }
}

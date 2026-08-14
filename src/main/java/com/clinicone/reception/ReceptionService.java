package com.clinicone.reception;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentResponse;
import com.clinicone.appointment.AppointmentService;
import com.clinicone.appointment.CreateAppointmentRequest;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.patientprofile.PatientProfileResponse;
import com.clinicone.queue.QueueService;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceptionService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ALLOWED_GENDERS = Set.of("Nam", "Nữ", "Khác");

    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final QueueTicketRepository ticketRepository;
    private final QueueService queueService;
    private final Clock clock;
    private final PatientAccountRepository patientAccountRepository;
    private final AppointmentService appointmentService;
    private final PatientProfileRepository patientProfileRepository;
    private final BusinessLogService businessLogService;

    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock) {
        this(appointmentRepository, doctorProfileRepository, ticketRepository, queueService, clock, null, null, null, null);
    }

    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock,
                            PatientAccountRepository patientAccountRepository,
                            AppointmentService appointmentService,
                            PatientProfileRepository patientProfileRepository) {
        this(appointmentRepository, doctorProfileRepository, ticketRepository, queueService, clock,
                patientAccountRepository, appointmentService, patientProfileRepository, null);
    }

    @Autowired
    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock,
                            PatientAccountRepository patientAccountRepository,
                            AppointmentService appointmentService,
                            PatientProfileRepository patientProfileRepository,
                            BusinessLogService businessLogService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.ticketRepository = ticketRepository;
        this.queueService = queueService;
        this.clock = clock;
        this.patientAccountRepository = patientAccountRepository;
        this.appointmentService = appointmentService;
        this.patientProfileRepository = patientProfileRepository;
        this.businessLogService = businessLogService;
    }

    @Transactional(readOnly = true)
    public List<ReceptionAppointmentResponse> search(String query, LocalDate date) {
        String normalized = normalizeQuery(query);
        LocalDate appointmentDate = date == null ? today() : date;
        return appointmentRepository.findReceptionCandidatesByStatuses(normalized, appointmentDate,
                        List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReceptionAppointmentResponse checkIn(UUID appointmentId, ReceptionCheckInRequest request) {
        return checkIn(appointmentId, request, null);
    }

    @Transactional
    public ReceptionAppointmentResponse checkIn(UUID appointmentId, ReceptionCheckInRequest request, String actor) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        PatientAccount patient = appointment.getPatient();
        if (patient != null && patient.getStatus() == AccountStatus.LOCKED) {
            throw new AuthException(HttpStatus.CONFLICT, "PATIENT_ACCOUNT_LOCKED",
                    "Tài khoản người bệnh đang bị khóa.");
        }
        if (patient != null && patient.isMustChangePassword()) {
            throw new AuthException(HttpStatus.CONFLICT, "PASSWORD_CHANGE_REQUIRED",
                    "Người bệnh cần đổi mật khẩu tạm trước khi check-in.");
        }
        QueueTicketResponse ticket = actor == null
                ? queueService.checkInByStaff(request.roomCode().trim(), appointmentId, request.reason().trim())
                : queueService.checkInByStaff(request.roomCode().trim(), appointmentId, request.reason().trim(), actor);
        return toResponse(appointment, ticket);
    }

    /** Moves a same-day late appointment while preserving its appointment code. */
    @Transactional
    public ReceptionAppointmentResponse rescheduleLate(UUID appointmentId, ReceptionRebookRequest request,
                                                       String actor) {
        if (appointmentService == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_RESCHEDULE_UNAVAILABLE",
                    "Chưa bật luồng chuyển lịch đến muộn tại quầy.");
        }
        Appointment previous = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        DoctorProfile doctor = doctorProfileRepository.findById(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_ASSIGNMENT_NOT_FOUND",
                        "Không tìm thấy bác sĩ đang được phân công."));
        if (!doctor.getSpecialty().equalsIgnoreCase(previous.getSpecialty())) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SPECIALTY_MISMATCH",
                    "Bác sĩ mới phải thuộc cùng chuyên khoa với lịch cũ.");
        }
        appointmentService.rescheduleForReception(appointmentId, request.doctorId(),
                doctor.getStaffAccount().getFullName(), request.appointmentDate(), request.startTime(),
                request.lateReason(), actor);
        Appointment saved = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn vừa chuyển."));
        return toResponse(saved, null);
    }
    
    @Transactional
    public ReceptionAppointmentResponse rebookAbsent(UUID appointmentId, ReceptionRebookRequest request, String actor) {
        if (patientAccountRepository == null || appointmentService == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_REBOOK_UNAVAILABLE",
                    "Chưa bật luồng đặt lại lịch tại quầy.");
        }
        Appointment previous = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        if (previous.getStatus() != AppointmentStatus.ABSENT) {
            throw new AuthException(HttpStatus.CONFLICT, "REBOOK_STATUS_INVALID",
                    "Chỉ lịch đã ghi nhận vắng mặt mới được đặt lại tại quầy.");
        }

        DoctorProfile doctor = doctorProfileRepository.findById(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_ASSIGNMENT_NOT_FOUND",
                        "Không tìm thấy bác sĩ đang được phân công."));
        if (!doctor.getSpecialty().equalsIgnoreCase(previous.getSpecialty())) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SPECIALTY_MISMATCH",
                    "Bác sĩ mới phải thuộc cùng chuyên khoa với lịch cũ.");
        }

        CreateAppointmentRequest replacementRequest = new CreateAppointmentRequest(
                previous.getSpecialty(), doctor.getStaffAccount().getFullName(), request.appointmentDate(),
                request.startTime(), previous.getReason(),
                previous.getPatientProfile() == null ? null : previous.getPatientProfile().getId(),
                request.doctorId(), null, previous.getServiceId());
        AppointmentResponse created = previous.getPatient() == null
                ? appointmentService.createTemporary(previous.getPatientProfile(), replacementRequest)
                : appointmentService.create(previous.getPatient().getId().toString(), replacementRequest);
        Appointment replacement = appointmentRepository.findById(created.id())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch mới vừa tạo."));
        if (businessLogService != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", replacement.getId(),
                    AppointmentStatus.ABSENT.name(), AppointmentStatus.BOOKED.name(), "RECEPTION_REBOOK_ABSENT",
                    actor, request.lateReason().trim());
        }
        return toResponse(replacement, null);
    }

    @Transactional
    public ReceptionAppointmentResponse leaveBeforeExam(UUID appointmentId, String reason) {
        return leaveBeforeExam(appointmentId, reason, null);
    }

    @Transactional
    public ReceptionAppointmentResponse leaveBeforeExam(UUID appointmentId, String reason, String actor) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        QueueTicketResponse existingTicket = ticketRepository.findByAppointmentId(appointmentId)
                .map(ticket -> actor == null
                        ? queueService.leaveBeforeExam(ticket.getId(), reason)
                        : queueService.leaveBeforeExam(ticket.getId(), reason, actor))
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_NOT_FOUND",
                        "Lịch hẹn chưa có lượt trong hàng đợi."));
        return toResponse(appointment, existingTicket);
    }

    @Transactional
    public ReceptionAppointmentResponse markFacilityUnavailable(UUID appointmentId, String reason, String actor) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        QueueTicketResponse ticket = ticketRepository.findByAppointmentId(appointmentId)
                .map(value -> queueService.markFacilityUnavailable(value.getId(), reason, actor))
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_NOT_FOUND",
                        "Lịch hẹn chưa có lượt trong hàng đợi."));
        return toResponse(appointment, ticket);
    }

    @Transactional(readOnly = true)
    public List<ReceptionPatientProfileResponse> profiles(String phone) {
        if (patientAccountRepository == null || patientProfileRepository == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_PROFILES_UNAVAILABLE",
                    "Chưa bật tra cứu hồ sơ tại quầy.");
        }
        PatientAccount patient = patientAccountRepository.findByPhone(normalizePhone(phone))
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PATIENT_ACCOUNT_REQUIRED",
                        "Chưa tìm thấy tài khoản theo số điện thoại."));
        return patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(patient.getId())
                .stream().map(ReceptionPatientProfileResponse::from).toList();
    }

    @Transactional
    public ReceptionPatientProfileResponse createTemporaryProfile(ReceptionTemporaryProfileRequest request) {
        if (patientAccountRepository == null || patientProfileRepository == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_PROFILES_UNAVAILABLE",
                    "Chưa bật tạo hồ sơ tại quầy.");
        }
        String phone = normalizePhone(request.phone());
        if (!ALLOWED_GENDERS.contains(request.gender().trim())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "GENDER_INVALID",
                    "Vui lòng chọn giới tính hợp lệ.");
        }
        if (patientAccountRepository.findByPhone(phone).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED",
                    "Số điện thoại đã có tài khoản; hãy chọn hồ sơ hiện có.");
        }
        PatientProfile profile = patientProfileRepository.findFirstByTemporaryProfileTrueAndOwnerIsNullAndPhone(phone)
                .orElseGet(() -> PatientProfile.createTemporary(phoneValue(request.fullName()), request.dateOfBirth(),
                        phoneValue(request.gender()), phone, phoneValue(request.identityNumber()),
                        phoneValue(request.nationality()), phoneValue(request.ethnicity()), phoneValue(request.address())));
        return ReceptionPatientProfileResponse.from(patientProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<ReceptionDoctorOptionResponse> doctors() {
        return doctorProfileRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(DoctorProfile::isActive)
                .map(ReceptionDoctorOptionResponse::from)
                .toList();
    }

    /** Tiếp nhận người bệnh đến quầy mà chưa có lịch trong ngày. */
    @Transactional
    public ReceptionAppointmentResponse createWalkIn(ReceptionWalkInRequest request) {
        return createWalkIn(request, null, null);
    }

    @Transactional
    public ReceptionAppointmentResponse createWalkIn(ReceptionWalkInRequest request, String actor) {
        return createWalkIn(request, actor, null);
    }

    @Transactional
    public ReceptionAppointmentResponse createWalkIn(ReceptionWalkInRequest request, String actor,
                                                     String requestKey) {
        if (patientAccountRepository == null || appointmentService == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_WALK_IN_UNAVAILABLE",
                    "Chưa bật luồng tiếp nhận tại quầy.");
        }
        String phone = normalizePhone(request.phone());
        PatientAccount patient = patientAccountRepository.findByPhone(phone).orElse(null);
        PatientProfile temporaryProfile = null;
        if (patient != null) {
            if (patient.getStatus() == AccountStatus.LOCKED) {
                throw new AuthException(HttpStatus.CONFLICT, "PATIENT_ACCOUNT_LOCKED",
                        "Tài khoản người bệnh đang bị khóa.");
            }
            if (patient.isMustChangePassword()) {
                throw new AuthException(HttpStatus.CONFLICT, "PASSWORD_CHANGE_REQUIRED",
                        "Người bệnh cần đổi mật khẩu tạm trước khi check-in.");
            }
        } else {
            if (request.profileId() == null || patientProfileRepository == null) {
                throw new AuthException(HttpStatus.NOT_FOUND, "TEMPORARY_PROFILE_REQUIRED",
                        "Chưa có tài khoản. Hãy tạo hồ sơ tạm và đối chiếu trước khi tiếp nhận.");
            }
            temporaryProfile = patientProfileRepository.findById(request.profileId())
                    .filter(PatientProfile::isTemporaryProfile)
                    .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "TEMPORARY_PROFILE_NOT_FOUND",
                            "Không tìm thấy hồ sơ tạm phù hợp."));
            if (!phone.equals(temporaryProfile.getPhone())) {
                throw new AuthException(HttpStatus.CONFLICT, "TEMPORARY_PROFILE_PHONE_MISMATCH",
                        "Số điện thoại không khớp với hồ sơ tạm.");
            }
            validateTemporaryExceptionReason(request.exceptionReason());
        }

        LocalDate appointmentDate = request.appointmentDate();
        if (!today().equals(appointmentDate)) {
            throw new AuthException(HttpStatus.CONFLICT, "WALK_IN_TODAY_ONLY",
                    "Tiếp nhận không có lịch chỉ áp dụng cho ngày hôm nay.");
        }

        DoctorProfile doctor = doctorProfileRepository.findById(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_ASSIGNMENT_NOT_FOUND",
                        "Không tìm thấy bác sĩ đang được phân công."));

        if (Boolean.TRUE.equals(request.overCapacity())) {
            // Serialize exception admissions per doctor so the check-and-insert
            // cannot allow a fourth admission under concurrent reception users.
            doctorProfileRepository.findByStaffAccount_IdForUpdate(request.doctorId())
                    .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_ASSIGNMENT_NOT_FOUND",
                            "Không tìm thấy bác sĩ đang được phân công."));
            validateOverCapacityReason(request.exceptionReason());
            long existingOverCapacity = appointmentRepository
                    .countByDoctorStaffIdAndAppointmentDateAndOverCapacityTrueAndStatusNot(
                            request.doctorId(), appointmentDate, AppointmentStatus.CANCELLED);
            if (existingOverCapacity >= 3) {
                throw new AuthException(HttpStatus.CONFLICT, "WALK_IN_OVER_CAPACITY_LIMIT",
                        "Bác sĩ đã đủ 3 lượt tiếp nhận ngoài công suất trong ngày.");
            }
        }
        String normalizedRequestKey = normalizeRequestKey(requestKey);

        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                doctor.getSpecialty(), doctor.getStaffAccount().getFullName(), appointmentDate,
                request.startTime(), request.reason(), request.profileId(), request.doctorId());
        AppointmentResponse created;
        if (patient == null) {
            if (Boolean.TRUE.equals(request.overCapacity())) {
                created = normalizedRequestKey == null
                        ? appointmentService.createTemporaryReception(temporaryProfile, appointmentRequest)
                        : appointmentService.createTemporaryReception(temporaryProfile, appointmentRequest, normalizedRequestKey);
            } else {
                created = normalizedRequestKey == null
                        ? appointmentService.createTemporary(temporaryProfile, appointmentRequest)
                        : appointmentService.createTemporary(temporaryProfile, appointmentRequest, normalizedRequestKey);
            }
        } else if (Boolean.TRUE.equals(request.overCapacity())) {
            created = normalizedRequestKey == null
                    ? appointmentService.createReception(patient.getId().toString(), appointmentRequest)
                    : appointmentService.createReception(patient.getId().toString(), appointmentRequest, normalizedRequestKey);
        } else {
            created = normalizedRequestKey == null
                    ? appointmentService.create(patient.getId().toString(), appointmentRequest)
                    : appointmentService.create(patient.getId().toString(), appointmentRequest, normalizedRequestKey);
        }
        QueueTicketResponse ticket = actor == null
                ? queueService.checkInByStaff(doctor.getRoom().getCode(), created.id(), request.exceptionReason())
                : queueService.checkInByStaff(doctor.getRoom().getCode(), created.id(), request.exceptionReason(), actor);
        Appointment appointment = appointmentRepository.findById(created.id())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn vừa tạo."));
        return toResponse(appointment, ticket);
    }

    private ReceptionAppointmentResponse toResponse(Appointment appointment) {
        return toResponse(appointment, ticketRepository.findByAppointmentId(appointment.getId())
                .map(QueueTicketResponse::from).orElse(null));
    }

    private ReceptionAppointmentResponse toResponse(Appointment appointment, QueueTicketResponse ticket) {
        DoctorProfile profile = appointment.getDoctorStaffId() == null ? null
                : doctorProfileRepository.findByStaffAccount_Id(appointment.getDoctorStaffId())
                .filter(DoctorProfile::isActive).orElse(null);
        return ReceptionAppointmentResponse.from(appointment,
                profile == null ? null : profile.getRoom().getCode(),
                profile == null ? null : profile.getRoom().getName(), ticket);
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 3 || normalized.length() > 120) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "RECEPTION_QUERY_INVALID",
                    "Nhập mã lịch hẹn hoặc số điện thoại hợp lệ.");
        }
        return normalized;
    }

    private void validateOverCapacityReason(String reason) {
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "OVER_CAPACITY_REASON_INVALID",
                    "Lý do nhận ngoài công suất phải từ 10 đến 500 ký tự.");
        }
    }

    private String normalizeRequestKey(String requestKey) {
        String normalized = requestKey == null ? "" : requestKey.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 80) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Khóa chống trùng không được dài quá 80 ký tự.");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!normalized.matches("0\\d{9}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        }
        return normalized;
    }

    private String phoneValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTemporaryExceptionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TEMPORARY_EXCEPTION_REASON_INVALID",
                    "Lý do không thể xác thực phải dài từ 10 đến 500 ký tự.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }
}

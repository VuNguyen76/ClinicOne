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
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.patientprofile.PatientProfileResponse;
import com.clinicone.queue.QueueService;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ReceptionService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final QueueTicketRepository ticketRepository;
    private final QueueService queueService;
    private final Clock clock;
    private final PatientAccountRepository patientAccountRepository;
    private final AppointmentService appointmentService;
    private final PatientProfileRepository patientProfileRepository;

    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock) {
        this(appointmentRepository, doctorProfileRepository, ticketRepository, queueService, clock, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock,
                            PatientAccountRepository patientAccountRepository,
                            AppointmentService appointmentService,
                            PatientProfileRepository patientProfileRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.ticketRepository = ticketRepository;
        this.queueService = queueService;
        this.clock = clock;
        this.patientAccountRepository = patientAccountRepository;
        this.appointmentService = appointmentService;
        this.patientProfileRepository = patientProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<ReceptionAppointmentResponse> search(String query, LocalDate date) {
        String normalized = normalizeQuery(query);
        LocalDate appointmentDate = date == null ? today() : date;
        return appointmentRepository.findReceptionCandidatesByStatuses(normalized, appointmentDate,
                        List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReceptionAppointmentResponse checkIn(UUID appointmentId, ReceptionCheckInRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        QueueTicketResponse ticket = queueService.checkInByStaff(request.roomCode().trim(), appointmentId, request.reason().trim());
        return toResponse(appointment, ticket);
    }

    @Transactional
    public ReceptionAppointmentResponse leaveBeforeExam(UUID appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        QueueTicketResponse existingTicket = ticketRepository.findByAppointmentId(appointmentId)
                .map(ticket -> queueService.leaveBeforeExam(ticket.getId(), reason))
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

    @Transactional(readOnly = true)
    public List<ReceptionDoctorOptionResponse> doctors() {
        return doctorProfileRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(DoctorProfile::isActive)
                .map(ReceptionDoctorOptionResponse::from)
                .toList();
    }

    /**
     * Tiếp nhận người bệnh đến quầy mà chưa có lịch trong ngày. Hệ thống chỉ
     * cho phép chọn tài khoản đã tồn tại; việc tạo tài khoản/hồ sơ tạm cần OTP
     * được xử lý ở luồng riêng, không tự sinh dữ liệu thiếu thông tin ở đây.
     */
    @Transactional
    public ReceptionAppointmentResponse createWalkIn(ReceptionWalkInRequest request) {
        if (patientAccountRepository == null || appointmentService == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "RECEPTION_WALK_IN_UNAVAILABLE",
                    "Chưa bật luồng tiếp nhận tại quầy.");
        }
        String phone = normalizePhone(request.phone());
        PatientAccount patient = patientAccountRepository.findByPhone(phone)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PATIENT_ACCOUNT_REQUIRED",
                        "Chưa tìm thấy tài khoản theo số điện thoại. Hãy hỗ trợ người bệnh đăng ký trước."));
        if (patient.getStatus() == AccountStatus.LOCKED) {
            throw new AuthException(HttpStatus.CONFLICT, "PATIENT_ACCOUNT_LOCKED",
                    "Tài khoản người bệnh đang bị khóa.");
        }
        if (patient.isMustChangePassword()) {
            throw new AuthException(HttpStatus.CONFLICT, "PASSWORD_CHANGE_REQUIRED",
                    "Người bệnh cần đổi mật khẩu tạm trước khi check-in.");
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

        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                doctor.getSpecialty(), doctor.getStaffAccount().getFullName(), appointmentDate,
                request.startTime(), request.reason(), request.profileId(), request.doctorId());
        AppointmentResponse created = appointmentService.create(patient.getId().toString(), appointmentRequest);
        QueueTicketResponse ticket = queueService.checkInByStaff(
                doctor.getRoom().getCode(), created.id(), request.exceptionReason());
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

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!normalized.matches("0\\d{9}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        }
        return normalized;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }
}

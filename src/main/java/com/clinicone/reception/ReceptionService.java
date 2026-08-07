package com.clinicone.reception;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
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

    public ReceptionService(AppointmentRepository appointmentRepository,
                            DoctorProfileRepository doctorProfileRepository,
                            QueueTicketRepository ticketRepository,
                            QueueService queueService,
                            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.ticketRepository = ticketRepository;
        this.queueService = queueService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReceptionAppointmentResponse> search(String query, LocalDate date) {
        String normalized = normalizeQuery(query);
        LocalDate appointmentDate = date == null ? today() : date;
        return appointmentRepository.findReceptionCandidates(normalized, appointmentDate, AppointmentStatus.BOOKED)
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

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }
}

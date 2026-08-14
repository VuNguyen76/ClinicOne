package com.clinicone.reconciliation;

import com.clinicone.audit.BusinessLogService;
import com.clinicone.audit.BusinessLog;
import com.clinicone.audit.BusinessLogRepository;
import com.clinicone.auth.AuthException;
import com.clinicone.queue.QueueService;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.schedule.ScheduleTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The common correction action is recorded as a real business activity. If
 * recording fails, the surrounding close transaction is rolled back and the
 * incident remains open for a safe retry.
 */
@Component
public class BusinessLogReconciliationActionDispatcher implements ReconciliationActionDispatcher {
    private final BusinessLogService businessLogService;
    private final BusinessLogRepository businessLogRepository;
    private final QueueService queueService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ScheduleTemplateService scheduleTemplateService;

    public BusinessLogReconciliationActionDispatcher(BusinessLogService businessLogService,
                                                     BusinessLogRepository businessLogRepository,
                                                     QueueService queueService,
                                                     AppointmentRepository appointmentRepository,
                                                     DoctorProfileRepository doctorProfileRepository,
                                                     ScheduleTemplateService scheduleTemplateService) {
        this.businessLogService = businessLogService;
        this.businessLogRepository = businessLogRepository;
        this.queueService = queueService;
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.scheduleTemplateService = scheduleTemplateService;
    }

    @Override
    public void dispatch(ReconciliationIncident incident, CloseReconciliationRequest request, String actor) {
        if (request.action() == ReconciliationAction.NO_ACTION_REQUIRED) {
            recordActivity(incident, request, actor);
            return;
        }
        if (request.referenceType() != ReconciliationReferenceType.BUSINESS_LOG) {
            throw unsupported("Muốn chạy lại nghiệp vụ phải tham chiếu đúng mã nhật ký nghiệp vụ.");
        }
        BusinessLog log = businessLogRepository.findById(UUID.fromString(request.referenceValue()))
                .orElseThrow(() -> unsupported("Không tìm thấy nhật ký để chạy lại nghiệp vụ."));
        if (!log.getEntityType().equalsIgnoreCase(incident.getEntityType())
                || !log.getEntityId().equals(incident.getEntityId())) {
            throw unsupported("Nhật ký không thuộc đúng đối tượng cần đối soát.");
        }
        String eventType = log.getEventType();
        if ("QUEUE_TICKET".equalsIgnoreCase(log.getEntityType()) && "CALL_PATIENT".equals(eventType)) {
            UUID ticketId = log.getEntityId();
            queueService.call(ticketId, actor);
        } else if ("QUEUE_TICKET".equalsIgnoreCase(log.getEntityType()) && "START_EXAMINATION".equals(eventType)) {
            UUID ticketId = log.getEntityId();
            queueService.start(ticketId, actor);
        } else if ("QUEUE_TICKET".equalsIgnoreCase(log.getEntityType()) && "LEAVE_BEFORE_EXAM".equals(eventType)) {
            UUID ticketId = log.getEntityId();
            queueService.leaveBeforeExam(ticketId, request.resultNote(), actor);
        } else if ("QUEUE_TICKET".equalsIgnoreCase(log.getEntityType()) && "FACILITY_UNAVAILABLE".equals(eventType)) {
            UUID ticketId = log.getEntityId();
            queueService.markFacilityUnavailable(ticketId, request.resultNote(), actor);
        } else if ("APPOINTMENT".equalsIgnoreCase(log.getEntityType()) && "CHECK_IN".equals(eventType)) {
            replayCheckIn(log, request, actor);
        } else if ("WORK_SCHEDULE_TEMPLATE".equalsIgnoreCase(log.getEntityType())
                && ("GENERATE_SLOTS".equals(eventType) || "REGENERATE_SLOTS".equals(eventType))) {
            scheduleTemplateService.regenerate(log.getEntityId());
        } else {
            throw unsupported("Nghiệp vụ " + eventType + " chưa có bộ xử lý chạy lại an toàn.");
        }
        recordActivity(incident, request, actor);
    }

    private void replayCheckIn(BusinessLog log, CloseReconciliationRequest request, String actor) {
        Appointment appointment = appointmentRepository.findById(log.getEntityId())
                .orElseThrow(() -> unsupported("Không tìm thấy lịch hẹn để chạy lại check-in."));
        if (appointment.getDoctorStaffId() == null) {
            throw unsupported("Lịch hẹn chưa gắn bác sĩ/phòng nên chưa thể chạy lại check-in.");
        }
        String roomCode = doctorProfileRepository.findByStaffAccount_Id(appointment.getDoctorStaffId())
                .map(profile -> profile.getRoom().getCode())
                .orElseThrow(() -> unsupported("Bác sĩ chưa được gắn phòng nên chưa thể chạy lại check-in."));
        queueService.checkInByStaff(roomCode, appointment.getId(), request.resultNote(), actor,
                "reconciliation-" + log.getId());
    }

    private void recordActivity(ReconciliationIncident incident, CloseReconciliationRequest request, String actor) {
        businessLogService.recordActivity(UUID.randomUUID(), incident.getEntityType(), incident.getEntityId(),
                null, "RECONCILED", "RECONCILIATION_" + request.action().name(), actor, request.resultNote());
    }

    private AuthException unsupported(String message) {
        return new AuthException(HttpStatus.CONFLICT, "RECONCILIATION_REPLAY_UNSUPPORTED", message);
    }
}

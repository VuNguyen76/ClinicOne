package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

@Service
public class ClinicServiceManagementService {
    private final ClinicServiceRepository repository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final SpecialtyCatalogService specialtyCatalog;

    public ClinicServiceManagementService(ClinicServiceRepository repository,
                                           DoctorProfileRepository doctorProfileRepository,
                                           SpecialtyCatalogService specialtyCatalog) {
        this.repository = repository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.specialtyCatalog = specialtyCatalog;
    }

    @Transactional(readOnly = true)
    public List<ClinicServiceResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClinicServiceResponse> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClinicServiceResponse create(CreateClinicServiceRequest request) {
        String name = normalize(request.name(), "Tên dịch vụ không được để trống.");
        String visitType = normalize(request.visitType(), "Loại lượt khám không được để trống.");
        validateDuration(request.durationMinutes());
        String specialty = canonicalSpecialty(request.specialty());
        if (repository.existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCase(name, specialty, visitType)) {
            throw conflict("SERVICE_EXISTS", "Dịch vụ cùng tên, chuyên khoa và loại lượt đã tồn tại.");
        }
        List<DoctorProfile> doctors = resolveDoctors(request.doctorIds(), specialty);
        ClinicService service = ClinicService.create(name, specialty, visitType, request.durationMinutes(),
                requiresMedicalRecord(request.requiresMedicalRecord()), doctors);
        return toResponse(repository.save(service));
    }

    @Transactional
    public ClinicServiceResponse update(UUID id, UpdateClinicServiceRequest request) {
        ClinicService service = find(id);
        String name = normalize(request.name(), "Tên dịch vụ không được để trống.");
        String visitType = normalize(request.visitType(), "Loại lượt khám không được để trống.");
        validateDuration(request.durationMinutes());
        String specialty = canonicalSpecialty(request.specialty());
        if (repository.existsByNameIgnoreCaseAndSpecialtyIgnoreCaseAndVisitTypeIgnoreCaseAndIdNot(
                name, specialty, visitType, id)) {
            throw conflict("SERVICE_EXISTS", "Dịch vụ cùng tên, chuyên khoa và loại lượt đã tồn tại.");
        }
        service.update(name, specialty, visitType, request.durationMinutes(),
                requiresMedicalRecord(request.requiresMedicalRecord()), resolveDoctors(request.doctorIds(), specialty));
        return toResponse(repository.save(service));
    }

    @Transactional
    public ClinicServiceResponse setActive(UUID id, boolean active) {
        ClinicService service = find(id);
        service.setActive(active);
        return toResponse(repository.save(service));
    }

    private ClinicService find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND,
                "SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ khám."));
    }

    private String canonicalSpecialty(String specialty) {
        return specialtyCatalog.require(normalize(specialty, "Chuyên khoa không được để trống.")).name();
    }

    private List<DoctorProfile> resolveDoctors(List<UUID> doctorIds, String specialty) {
        if (doctorIds == null || doctorIds.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SERVICE_DOCTOR_REQUIRED",
                    "Dịch vụ phải có ít nhất một bác sĩ đủ điều kiện.");
        }
        Set<UUID> uniqueIds = new HashSet<>(doctorIds);
        if (uniqueIds.size() != doctorIds.size()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SERVICE_DOCTOR_DUPLICATE",
                    "Danh sách bác sĩ đủ điều kiện không được trùng.");
        }
        List<DoctorProfile> doctors = doctorProfileRepository.findAllByStaffAccount_IdInAndActiveTrue(doctorIds);
        if (doctors.size() != uniqueIds.size()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SERVICE_DOCTOR_NOT_FOUND",
                    "Có bác sĩ không tồn tại hoặc chưa được phân công.");
        }
        if (doctors.stream().anyMatch(doctor -> !doctor.getSpecialty().equalsIgnoreCase(specialty))) {
            throw new AuthException(HttpStatus.CONFLICT, "SERVICE_DOCTOR_SPECIALTY_MISMATCH",
                    "Có bác sĩ không thuộc chuyên khoa của dịch vụ.");
        }
        return doctors;
    }

    private void validateDuration(int durationMinutes) {
        if (durationMinutes < 5 || durationMinutes > 120) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SERVICE_DURATION_INVALID",
                    "Thời lượng dịch vụ phải từ 5 đến 120 phút.");
        }
    }

    private String normalize(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new AuthException(HttpStatus.BAD_REQUEST, "SERVICE_FIELD_REQUIRED", message);
        return normalized;
    }

    private ClinicServiceResponse toResponse(ClinicService service) {
        List<EligibleDoctorResponse> doctors = service.getEligibleDoctors().stream()
                .map(EligibleDoctorResponse::from)
                .sorted(Comparator.comparing(EligibleDoctorResponse::fullName))
                .toList();
        return new ClinicServiceResponse(service.getId(), service.getName(), service.getSpecialty(),
                service.getVisitType(), service.getDurationMinutes(), service.isActive(), doctors,
                service.requiresMedicalRecord());
    }

    private boolean requiresMedicalRecord(Boolean value) {
        return value == null || value;
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}

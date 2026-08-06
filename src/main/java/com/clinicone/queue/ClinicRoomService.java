package com.clinicone.queue;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClinicRoomService {
    private final ClinicRoomRepository repository;

    public ClinicRoomService(ClinicRoomRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ClinicRoomResponse> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(ClinicRoomResponse::from).toList();
    }

    @Transactional
    public ClinicRoomResponse create(CreateClinicRoomRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "ROOM_CODE_EXISTS", "Mã phòng đã tồn tại.");
        }
        ClinicRoom room = ClinicRoom.create(code, request.name(), request.specialty());
        return ClinicRoomResponse.from(repository.save(room));
    }

    @Transactional
    public ClinicRoomResponse update(UUID id, UpdateClinicRoomRequest request) {
        ClinicRoom room = find(id);
        String code = normalizeCode(request.code());
        if (!room.getCode().equalsIgnoreCase(code) && repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "ROOM_CODE_EXISTS", "Mã phòng đã tồn tại.");
        }
        room.update(code, request.name(), request.specialty());
        return ClinicRoomResponse.from(repository.save(room));
    }

    @Transactional
    public ClinicRoomResponse setActive(UUID id, boolean active) {
        ClinicRoom room = find(id);
        room.setActive(active);
        return ClinicRoomResponse.from(repository.save(room));
    }

    private ClinicRoom find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Không tìm thấy phòng khám."));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

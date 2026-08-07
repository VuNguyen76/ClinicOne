package com.clinicone.queue;

import java.util.UUID;

public record ClinicRoomResponse(UUID id, String code, String name, String specialty, boolean active, String qrToken) {
    public static ClinicRoomResponse from(ClinicRoom room) {
        return new ClinicRoomResponse(room.getId(), room.getCode(), room.getName(), room.getSpecialty(), room.isActive(), room.ensureQrToken());
    }
}

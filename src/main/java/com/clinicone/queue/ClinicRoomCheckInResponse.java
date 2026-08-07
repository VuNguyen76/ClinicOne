package com.clinicone.queue;

public record ClinicRoomCheckInResponse(String code, String name, String specialty) {
    public static ClinicRoomCheckInResponse from(ClinicRoom room) {
        return new ClinicRoomCheckInResponse(room.getCode(), room.getName(), room.getSpecialty());
    }
}

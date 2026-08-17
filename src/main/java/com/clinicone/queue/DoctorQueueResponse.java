package com.clinicone.queue;

import java.util.List;

public record DoctorQueueResponse(
        String roomCode,
        String roomName,
        String specialty,
        String shiftStatus,
        List<QueueTicketResponse> tickets
) {
    public DoctorQueueResponse(String roomCode, String roomName, String specialty,
                               List<QueueTicketResponse> tickets) {
        this(roomCode, roomName, specialty, "ACTIVE", tickets);
    }
}

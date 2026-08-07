package com.clinicone.queue;

import java.util.List;

public record DoctorQueueResponse(
        String roomCode,
        String roomName,
        String specialty,
        List<QueueTicketResponse> tickets
) {
}

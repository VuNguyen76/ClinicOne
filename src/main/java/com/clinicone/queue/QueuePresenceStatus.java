package com.clinicone.queue;

/**
 * Read-only presence signal for a queue ticket. It is deliberately separate
 * from the queue lifecycle status: a ticket can remain WAITING while the
 * patient is temporarily away after being called.
 */
public enum QueuePresenceStatus {
    READY("Sẵn sàng"),
    RETURN_REQUIRED("Chờ quay lại");

    private final String label;

    QueuePresenceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

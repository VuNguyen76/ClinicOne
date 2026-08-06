package com.clinicone.queue;

public enum QueueTicketStatus {
    WAITING("Đang chờ"),
    CALLED("Đang được gọi"),
    IN_SERVICE("Đang khám"),
    SKIPPED("Đã bỏ qua"),
    COMPLETED("Đã hoàn tất");

    private final String label;

    QueueTicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

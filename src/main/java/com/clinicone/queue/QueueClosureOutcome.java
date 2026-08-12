package com.clinicone.queue;

/** The operational result when a queue ticket is closed. */
public enum QueueClosureOutcome {
    EXAMINATION_COMPLETED("Hoàn thành khám"),
    EXAMINATION_STOPPED("Dừng khám"),
    LEFT_BEFORE_EXAM("Rời trước khám"),
    FACILITY_UNAVAILABLE("Cơ sở không thể phục vụ");

    private final String label;

    QueueClosureOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package com.clinicone.examination;

public enum ExaminationSessionStatus {
    SCHEDULED("Đã tạo"),
    CHECKED_IN("Đã check-in"),
    IN_PROGRESS("Đang khám"),
    COMPLETED("Đã hoàn thành"),
    ABSENT("Vắng mặt"),
    CANCELLED("Đã hủy");

    private final String label;

    ExaminationSessionStatus(String label) {
        this.label = label;
    }

    public String label() { return label; }
}

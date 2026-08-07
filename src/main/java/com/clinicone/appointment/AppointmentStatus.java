package com.clinicone.appointment;

public enum AppointmentStatus {
    BOOKED("Đã đặt"),
    CHECKED_IN("Đã check-in"),
    CANCELLED("Đã hủy"),
    ABSENT("Vắng mặt"),
    COMPLETED("Đã thực hiện"),
    NOT_PERFORMED("Không thực hiện");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

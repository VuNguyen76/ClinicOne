package com.clinicone.appointment;

public enum AppointmentStatus {
    BOOKED("Đã đặt"),
    CANCELLED("Đã hủy"),
    COMPLETED("Đã khám");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

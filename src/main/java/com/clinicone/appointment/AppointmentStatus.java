package com.clinicone.appointment;

public enum AppointmentStatus {
    BOOKED("Đã đặt"),
    CHECKED_IN("Đã check-in"),
    CANCELLED("Đã hủy"),
    COMPLETED("Đã khám"),
    NO_SHOW("Vắng mặt"),       
    NOT_ATTENDED("Không thực hiện");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

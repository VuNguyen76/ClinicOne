package com.clinicone.notification;

public interface SmsSender {
    void sendText(String phone, String message);
}

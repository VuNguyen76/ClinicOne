package com.clinicone.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class GmailOtpSender implements OtpSender {

    private final JavaMailSender mailSender;
    private final String from;

    public GmailOtpSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, OtpPurpose purpose, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("ClinicOne - Mã xác thực");
        message.setText("Mã xác thực ClinicOne của bạn là: " + code
                + "\nMã có hiệu lực trong 5 phút. Mục đích: " + purposeLabel(purpose)
                + ".\nNếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.");
        mailSender.send(message);
    }

    private String purposeLabel(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> "đăng ký tài khoản";
            case LOGIN -> "đăng nhập";
            case RECOVERY -> "khôi phục tài khoản";
        };
    }
}

package com.clinicone.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.clinicone.notification.SmsSender;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "textbee")
public class TextBeeOtpSender implements OtpSender, SmsSender {

    private final RestClient client;
    private final String apiKey;
    private final String deviceId;

    public TextBeeOtpSender(
            @Value("${TEXTBEE_API_KEY:}") String apiKey,
            @Value("${TEXTBEE_DEVICE_ID:}") String deviceId,
            @Value("${TEXTBEE_BASE_URL:https://api.textbee.dev}") String baseUrl
    ) {
        this.apiKey = apiKey;
        this.deviceId = deviceId;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(8));

        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    @Override
    public void send(String phone, OtpPurpose purpose, String code) {
        if (apiKey.isBlank() || deviceId.isBlank()) {
            throw new IllegalStateException("TextBee API key and device ID are not configured");
        }
        sendRequest(phone, messageFor(purpose, code));
    }

    @Override
    public void sendText(String phone, String message) {
        if (apiKey.isBlank() || deviceId.isBlank()) {
            throw new IllegalStateException("TextBee API key and device ID are not configured");
        }
        sendRequest(phone, message);
    }

    private void sendRequest(String phone, String message) {
        client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/gateway/devices/{deviceId}/send-sms")
                        .build(deviceId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SmsRequest(List.of(phone), message))
                .retrieve()
                .toBodilessEntity();
    }

    private String messageFor(OtpPurpose purpose, String code) {
        return "ClinicOne: Mã xác thực của bạn là " + code
                + ". Mã có hiệu lực trong 5 phút cho " + purposeLabel(purpose) + ".";
    }

    private String purposeLabel(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> "đăng ký tài khoản";
            case LOGIN -> "đăng nhập";
            case RECOVERY -> "khôi phục tài khoản";
            case RECEIPT_LOOKUP -> "tra cứu phiếu khám và đơn thuốc";
        };
    }

    private record SmsRequest(List<String> recipients, String message) {
    }
}

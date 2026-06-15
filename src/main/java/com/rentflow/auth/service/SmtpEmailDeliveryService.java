package com.rentflow.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@Slf4j
@Service
public class SmtpEmailDeliveryService implements EmailDeliveryService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;
    private final String frontendBaseUrl;
    private final String mailHost;
    private final int mailPort;

    public SmtpEmailDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${rentflow.mail.enabled:false}") boolean enabled,
            @Value("${rentflow.mail.from:no-reply@rentflow.local}") String from,
            @Value("${rentflow.mail.frontend-base-url:http://localhost:3002}") String frontendBaseUrl,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.port:587}") int mailPort) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
    }

    @PostConstruct
    void logMailDeliveryConfig() {
        log.info("Mail delivery config: enabled={}, host={}, port={}, from={}",
                enabled,
                mailHost == null || mailHost.isBlank() ? "<unset>" : mailHost,
                mailPort,
                from);
    }

    @Override
    public void sendVerificationEmail(String to, String rawToken, Instant expiresAt) {
        String verificationUrl = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        sendMail(to, "Xác minh email RentFlow", """
                Xin chào,

                Vui lòng xác minh email RentFlow bằng liên kết sau:
                %s

                Liên kết có hiệu lực đến: %s.
                Nếu bạn không tạo tài khoản RentFlow, vui lòng bỏ qua email này.

                RentFlow
                """.formatted(verificationUrl, expiresAt), "verification");
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken, Instant expiresAt) {
        String resetUrl = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        sendMail(to, "Đặt lại mật khẩu RentFlow", """
                Xin chào,

                Bạn vừa yêu cầu đặt lại mật khẩu RentFlow. Vui lòng dùng liên kết sau:
                %s

                Liên kết có hiệu lực đến: %s.
                Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.

                RentFlow
                """.formatted(resetUrl, expiresAt), "password reset");
    }

    private void sendMail(String to, String subject, String text, String mailType) {
        if (!enabled) {
            log.info("[email-disabled] {} email not sent for {}", mailType, to);
            return;
        }

        if (mailHost == null || mailHost.isBlank()) {
            throw new EmailDeliveryException("Mail sender host is not configured");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new EmailDeliveryException("Mail sender is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("{} email sent to {}", mailType, to);
        } catch (MailException ex) {
            throw new EmailDeliveryException("Failed to send " + mailType + " email", ex);
        }
    }
}

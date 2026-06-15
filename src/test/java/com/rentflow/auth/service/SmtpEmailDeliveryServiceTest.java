package com.rentflow.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailDeliveryServiceTest {

    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private JavaMailSender mailSender;

    @Test
    void disabledModeDoesNotRequireMailSender() {
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                false,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "",
                1025);

        service.sendVerificationEmail(
                "alice@example.com",
                "raw-token",
                Instant.parse("2026-06-02T10:00:00Z"));

        verifyNoInteractions(mailSenderProvider);
    }

    @Test
    void enabledModeRequiresMailHost() {
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                true,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "",
                1025);

        assertThatThrownBy(() -> service.sendVerificationEmail(
                        "alice@example.com",
                        "raw-token",
                        Instant.parse("2026-06-02T10:00:00Z")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("host is not configured");
    }

    @Test
    void enabledModeRequiresMailSenderBean() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                true,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "smtp.example.com",
                587);

        assertThatThrownBy(() -> service.sendVerificationEmail(
                        "alice@example.com",
                        "raw-token",
                        Instant.parse("2026-06-02T10:00:00Z")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("Mail sender is not configured");
    }

    @Test
    void sendsVietnameseVerificationEmailWithRawTokenLink() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                true,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "smtp.example.com",
                587);

        service.sendVerificationEmail(
                "alice@example.com",
                "raw-token",
                Instant.parse("2026-06-02T10:00:00Z"));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@rentflow.local");
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("Xác minh email RentFlow");
        assertThat(message.getText())
                .contains("Vui lòng xác minh email RentFlow")
                .contains("http://localhost:3000/verify-email?token=raw-token")
                .contains("2026-06-02T10:00:00Z");
    }

    @Test
    void sendsVietnamesePasswordResetEmailWithRawTokenLink() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                true,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "smtp.example.com",
                587);

        service.sendPasswordResetEmail(
                "alice@example.com",
                "reset-token",
                Instant.parse("2026-06-02T10:30:00Z"));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@rentflow.local");
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu RentFlow");
        assertThat(message.getText())
                .contains("Bạn vừa yêu cầu đặt lại mật khẩu RentFlow")
                .contains("http://localhost:3000/reset-password?token=reset-token")
                .contains("2026-06-02T10:30:00Z");
    }

    @Test
    void mapsJavaMailFailureToEmailDeliveryException() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
                mailSenderProvider,
                true,
                "no-reply@rentflow.local",
                "http://localhost:3000",
                "smtp.example.com",
                587);

        assertThatThrownBy(() -> service.sendVerificationEmail(
                        "alice@example.com",
                        "raw-token",
                        Instant.parse("2026-06-02T10:00:00Z")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("Failed to send verification email");
    }
}

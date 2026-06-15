package com.rentflow.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.PasswordResetTokenRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceLoggingTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailDeliveryService emailDeliveryService;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUpLogger() {
        logger = (Logger) LoggerFactory.getLogger(PasswordService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogger() {
        logger.detachAppender(appender);
    }

    @Test
    void requestResetDoesNotLogRawToken() {
        AuthUser user = new AuthUser("alice@example.com", "hash", UserStatus.ACTIVE, true);
        when(authUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        PasswordService service = new PasswordService(
                authUserRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailDeliveryService);

        service.requestReset("alice@example.com");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("/reset-password?token=") || msg.contains("token="));
    }
}

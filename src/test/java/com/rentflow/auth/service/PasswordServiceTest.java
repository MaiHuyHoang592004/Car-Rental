package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.PasswordResetToken;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.PasswordResetTokenRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailDeliveryService emailDeliveryService;

    @Test
    void requestResetPersistsHashAndSendsRawTokenOnlyToDeliveryService() {
        AuthUser user = user();
        when(authUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        PasswordService service = service();

        service.requestReset(user.getEmail());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(passwordResetTokenRepository).markUnusedTokensAsUsed(eq(user.getId()), any(Instant.class));
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        verify(emailDeliveryService).sendPasswordResetEmail(
                eq(user.getEmail()),
                rawTokenCaptor.capture(),
                any(Instant.class));

        String rawToken = rawTokenCaptor.getValue();
        PasswordResetToken saved = tokenCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getTokenHash()).isEqualTo(PasswordService.sha256(rawToken));
        assertThat(saved.getTokenHash()).doesNotContain(rawToken);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void requestResetForUnknownEmailDoesNotCreateTokenOrSendEmail() {
        when(authUserRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        PasswordService service = service();

        service.requestReset("missing@example.com");

        verifyNoInteractions(passwordResetTokenRepository, emailDeliveryService);
    }

    @Test
    void requestResetSwallowsEmailDeliveryFailureToAvoidEnumeration() {
        AuthUser user = user();
        when(authUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        doThrow(new EmailDeliveryException("SMTP unavailable"))
                .when(emailDeliveryService)
                .sendPasswordResetEmail(eq(user.getEmail()), any(String.class), any(Instant.class));

        PasswordService service = service();

        assertThatNoException().isThrownBy(() -> service.requestReset(user.getEmail()));
    }

    private PasswordService service() {
        return new PasswordService(
                authUserRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailDeliveryService);
    }

    private AuthUser user() {
        AuthUser user = new AuthUser("alice@example.com", "hash", UserStatus.ACTIVE, true);
        user.setId(UUID.randomUUID());
        return user;
    }
}

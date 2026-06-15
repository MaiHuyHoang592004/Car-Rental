package com.rentflow.integration.auth;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.PasswordResetToken;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.PasswordResetTokenRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.auth.service.PasswordService;
import com.rentflow.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class PasswordResetIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        authUserRepository.deleteAll();
    }

    @Test
    @DisplayName("forgot-password returns 204 for unknown email (no enumeration)")
    void forgotPassword_unknownEmail_returns204_silently() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "nobody@example.com" }
                                """))
                .andExpect(status().isNoContent());
        assertThat(passwordResetTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("forgot-password creates a token for an existing user")
    void forgotPassword_knownEmail_createsToken() throws Exception {
        register("alice@example.com", "Password@123");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "alice@example.com" }
                                """))
                .andExpect(status().isNoContent());

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("forgot-password invalidates prior unused reset tokens")
    void forgotPassword_reRequestInvalidatesPriorUnusedToken() throws Exception {
        register("alice-again@example.com", "Password@123");
        AuthUser user = authUserRepository.findByEmail("alice-again@example.com").orElseThrow();
        String oldRawToken = "old-reset-token-1234567890";
        PasswordResetToken oldToken = passwordResetTokenRepository.save(new PasswordResetToken(
                user.getId(),
                PasswordService.sha256(oldRawToken),
                Instant.now().plusSeconds(600)));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "alice-again@example.com" }
                                """))
                .andExpect(status().isNoContent());

        PasswordResetToken reloadedOld = passwordResetTokenRepository.findById(oldToken.getId()).orElseThrow();
        assertThat(reloadedOld.getUsedAt()).isNotNull();
        assertThat(passwordResetTokenRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("reset-password with valid token succeeds and allows login with new password")
    void resetPassword_validToken_updatesHash() throws Exception {
        register("bob@example.com", "OldPassword@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bob@example.com",
                                  "password": "OldPassword@123"
                                }
                                """))
                .andExpect(status().isOk());
        AuthUser user = authUserRepository.findByEmail("bob@example.com").orElseThrow();

        String rawToken = "raw-reset-token-1234567890";
        PasswordResetToken token = passwordResetTokenRepository.save(new PasswordResetToken(
                user.getId(),
                PasswordService.sha256(rawToken),
                Instant.now().plusSeconds(600)));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword@456"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isNoContent());

        PasswordResetToken reloadedToken = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(reloadedToken.getUsedAt()).isNotNull();
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(refreshToken -> assertThat(refreshToken.getRevokedAt()).isNotNull());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bob@example.com",
                                  "password": "OldPassword@123"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bob@example.com",
                                  "password": "NewPassword@456"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reset-password with invalid token returns 409 INVALID_TOKEN")
    void resetPassword_invalidToken_returns409() throws Exception {
        register("carol@example.com", "Password@123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "no-such-token",
                                  "newPassword": "NewPassword@456"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "Test User"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }
}

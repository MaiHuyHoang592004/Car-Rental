package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.PasswordResetToken;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.PasswordResetTokenRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final AuthUserRepository authUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService emailDeliveryService;

    /**
     * Always succeeds silently to avoid leaking which emails are registered.
     * If a matching user exists, generates a single-use token and logs the
     * reset link (stub email delivery).
     */
    @Transactional
    public void requestReset(String email) {
        AuthUser user = authUserRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email (silent)");
            return;
        }
        String raw = generateOpaqueToken();
        String hash = sha256(raw);
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        passwordResetTokenRepository.markUnusedTokensAsUsed(user.getId(), Instant.now());
        passwordResetTokenRepository.save(new PasswordResetToken(
                user.getId(), hash, expiresAt));
        try {
            emailDeliveryService.sendPasswordResetEmail(user.getEmail(), raw, expiresAt);
        } catch (EmailDeliveryException ex) {
            log.warn("Password reset email delivery failed for {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = sha256(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleException(
                        "INVALID_TOKEN", "Reset token is invalid or expired"));
        if (!token.isUsable()) {
            throw new BusinessRuleException("INVALID_TOKEN", "Reset token is invalid or expired");
        }
        AuthUser user = authUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessRuleException(
                        "INVALID_TOKEN", "Reset token is invalid or expired"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockUntil(null);
        authUserRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        int revoked = refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        log.info("Password reset for user {}; revoked {} refresh tokens", user.getEmail(), revoked);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(AuthenticationException::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw AuthenticationException.invalidCredentials();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authUserRepository.save(user);

        int revoked = refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        log.info("Password changed for user {}; revoked {} refresh tokens", user.getEmail(), revoked);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

package com.rentflow.auth.service;

import java.time.Instant;

public interface EmailDeliveryService {

    void sendVerificationEmail(String to, String rawToken, Instant expiresAt);

    void sendPasswordResetEmail(String to, String rawToken, Instant expiresAt);
}

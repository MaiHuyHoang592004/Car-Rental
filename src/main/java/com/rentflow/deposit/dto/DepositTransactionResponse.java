package com.rentflow.deposit.dto;

import com.rentflow.deposit.entity.DepositTransaction;
import com.rentflow.deposit.entity.DepositTransactionStatus;
import com.rentflow.deposit.entity.DepositTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DepositTransactionResponse(
        UUID id,
        DepositTransactionType type,
        DepositTransactionStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        String providerRef,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public static DepositTransactionResponse from(DepositTransaction tx) {
        return new DepositTransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getProvider().name(),
                tx.getProviderRef(),
                tx.getErrorCode(),
                tx.getErrorMessage(),
                tx.getCreatedAt());
    }
}

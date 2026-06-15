package com.rentflow.deposit.dto;

import com.rentflow.deposit.entity.BookingDeposit;
import com.rentflow.deposit.entity.DepositStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepositResponse(
        UUID id,
        UUID bookingId,
        UUID customerId,
        UUID hostId,
        DepositStatus status,
        BigDecimal amount,
        BigDecimal heldAmount,
        BigDecimal releasedAmount,
        BigDecimal deductedAmount,
        String currency,
        Instant holdExpiresAt,
        Instant releaseEligibleAt,
        Instant releasedAt,
        String provider,
        String providerHoldId,
        String providerStatus,
        List<DepositTransactionResponse> transactions
) {
    public static DepositResponse from(BookingDeposit deposit, List<DepositTransactionResponse> transactions) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getBookingId(),
                deposit.getCustomerId(),
                deposit.getHostId(),
                deposit.getStatus(),
                deposit.getAmount(),
                deposit.getHeldAmount(),
                deposit.getReleasedAmount(),
                deposit.getDeductedAmount(),
                deposit.getCurrency(),
                deposit.getHoldExpiresAt(),
                deposit.getReleaseEligibleAt(),
                deposit.getReleasedAt(),
                deposit.getProvider().name(),
                deposit.getProviderHoldId(),
                deposit.getProviderStatus(),
                transactions);
    }
}

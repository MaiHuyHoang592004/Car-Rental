package com.rentflow.payout.dto;

import com.rentflow.payout.entity.HostPayout;
import com.rentflow.payout.entity.HostPayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HostPayoutResponse(
        UUID id,
        UUID bookingId,
        UUID hostId,
        UUID payoutAccountId,
        HostPayoutStatus status,
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal netAmount,
        String currency,
        String holdReason,
        String adminNote,
        UUID approvedBy,
        Instant approvedAt,
        UUID paidBy,
        Instant paidAt,
        UUID failedBy,
        Instant failedAt,
        Instant createdAt
) {
    public static HostPayoutResponse from(HostPayout payout) {
        return new HostPayoutResponse(
                payout.getId(),
                payout.getBookingId(),
                payout.getHostId(),
                payout.getPayoutAccountId(),
                payout.getStatus(),
                payout.getGrossAmount(),
                payout.getPlatformFeeAmount(),
                payout.getNetAmount(),
                payout.getCurrency(),
                payout.getHoldReason(),
                payout.getAdminNote(),
                payout.getApprovedBy(),
                payout.getApprovedAt(),
                payout.getPaidBy(),
                payout.getPaidAt(),
                payout.getFailedBy(),
                payout.getFailedAt(),
                payout.getCreatedAt());
    }
}

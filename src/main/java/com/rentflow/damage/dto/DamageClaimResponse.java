package com.rentflow.damage.dto;

import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DamageClaimResponse(
        UUID id,
        UUID bookingId,
        UUID hostId,
        UUID customerId,
        UUID checkOutReportId,
        DamageClaimStatus status,
        BigDecimal claimAmount,
        BigDecimal approvedAmount,
        String currency,
        String title,
        String description,
        String customerResponse,
        String adminResolutionNote,
        Instant submittedAt,
        Instant customerRespondedAt,
        Instant resolvedAt,
        Instant createdAt,
        List<DamageClaimEvidenceResponse> evidence
) {
    public static DamageClaimResponse from(DamageClaim claim, List<DamageClaimEvidenceResponse> evidence) {
        return new DamageClaimResponse(
                claim.getId(),
                claim.getBookingId(),
                claim.getHostId(),
                claim.getCustomerId(),
                claim.getCheckOutReportId(),
                claim.getStatus(),
                claim.getClaimAmount(),
                claim.getApprovedAmount(),
                claim.getCurrency(),
                claim.getTitle(),
                claim.getDescription(),
                claim.getCustomerResponse(),
                claim.getAdminResolutionNote(),
                claim.getSubmittedAt(),
                claim.getCustomerRespondedAt(),
                claim.getResolvedAt(),
                claim.getCreatedAt(),
                evidence);
    }
}

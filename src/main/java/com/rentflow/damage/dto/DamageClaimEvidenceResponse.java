package com.rentflow.damage.dto;

import com.rentflow.damage.entity.DamageClaimEvidence;
import com.rentflow.damage.entity.DamageClaimEvidenceType;

import java.time.Instant;
import java.util.UUID;

public record DamageClaimEvidenceResponse(
        UUID id,
        UUID fileId,
        DamageClaimEvidenceType evidenceType,
        String note,
        Instant createdAt
) {
    public static DamageClaimEvidenceResponse from(DamageClaimEvidence evidence) {
        return new DamageClaimEvidenceResponse(
                evidence.getId(),
                evidence.getFileId(),
                evidence.getEvidenceType(),
                evidence.getNote(),
                evidence.getCreatedAt());
    }
}

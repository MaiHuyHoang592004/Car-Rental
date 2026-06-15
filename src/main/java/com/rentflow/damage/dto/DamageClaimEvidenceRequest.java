package com.rentflow.damage.dto;

import com.rentflow.damage.entity.DamageClaimEvidenceType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DamageClaimEvidenceRequest(
        @NotNull UUID fileId,
        @NotNull DamageClaimEvidenceType evidenceType,
        String note
) {
}

package com.rentflow.damage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateDamageClaimRequest(
        UUID checkOutReportId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal claimAmount,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty @Valid List<DamageClaimEvidenceRequest> evidence
) {
}

package com.rentflow.deposit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DeductDepositRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        UUID damageClaimId,
        UUID lateReturnFeeId,
        @Size(max = 500) String reason
) {
}

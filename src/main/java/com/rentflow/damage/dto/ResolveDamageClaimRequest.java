package com.rentflow.damage.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDamageClaimRequest(
        @DecimalMin(value = "0.00") BigDecimal approvedAmount,
        @Size(max = 4000) String note
) {
}

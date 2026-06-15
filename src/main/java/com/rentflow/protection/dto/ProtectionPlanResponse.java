package com.rentflow.protection.dto;

import com.rentflow.protection.entity.ProtectionPlan;

import java.math.BigDecimal;
import java.util.UUID;

public record ProtectionPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        String priceType,
        BigDecimal priceAmount,
        BigDecimal deductibleAmount,
        BigDecimal maxCoverageAmount,
        boolean active
) {
    public static ProtectionPlanResponse from(ProtectionPlan plan) {
        return new ProtectionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceType().name(),
                plan.getPriceAmount(),
                plan.getDeductibleAmount(),
                plan.getMaxCoverageAmount(),
                plan.isActive());
    }
}

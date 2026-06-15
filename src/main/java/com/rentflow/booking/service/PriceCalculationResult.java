package com.rentflow.booking.service;

import java.math.BigDecimal;
import java.util.List;

public record PriceCalculationResult(
        long rentalDays,
        BigDecimal basePricePerDay,
        BigDecimal baseAmount,
        BigDecimal extraAmount,
        BigDecimal protectionFee,
        BigDecimal totalAmount,
        String currency,
        String protectionPlanCode,
        BigDecimal protectionDeductibleAmount,
        BigDecimal protectionMaxCoverageAmount,
        List<ExtraLineItem> extras) {

    public PriceCalculationResult(
            long rentalDays,
            BigDecimal basePricePerDay,
            BigDecimal baseAmount,
            BigDecimal extraAmount,
            BigDecimal totalAmount,
            String currency,
            List<ExtraLineItem> extras) {
        this(
                rentalDays,
                basePricePerDay,
                baseAmount,
                extraAmount,
                BigDecimal.ZERO,
                totalAmount,
                currency,
                null,
                null,
                null,
                extras);
    }

    public PriceCalculationResult withProtection(
            String planCode,
            BigDecimal planFee,
            BigDecimal deductibleAmount,
            BigDecimal maxCoverageAmount) {
        BigDecimal normalizedFee = planFee == null ? BigDecimal.ZERO : planFee;
        return new PriceCalculationResult(
                rentalDays,
                basePricePerDay,
                baseAmount,
                extraAmount,
                normalizedFee,
                baseAmount.add(extraAmount).add(normalizedFee),
                currency,
                planCode,
                deductibleAmount,
                maxCoverageAmount,
                extras);
    }
}

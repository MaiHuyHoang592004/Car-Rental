package com.rentflow.protection.service;

import java.math.BigDecimal;
import java.util.UUID;

public record ProtectionQuote(
        UUID planId,
        String planCode,
        String planName,
        BigDecimal planFee,
        BigDecimal deductibleAmount,
        BigDecimal maxCoverageAmount
) {
}

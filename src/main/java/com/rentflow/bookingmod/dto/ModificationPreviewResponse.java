package com.rentflow.bookingmod.dto;

import java.math.BigDecimal;

public record ModificationPreviewResponse(
        boolean eligible,
        BigDecimal priceDelta,
        BigDecimal feeAmount,
        String currency,
        String message
) {
}

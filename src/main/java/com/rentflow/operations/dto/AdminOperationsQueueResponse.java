package com.rentflow.operations.dto;

public record AdminOperationsQueueResponse(
        long openDamageClaims,
        long pendingBookingModifications,
        long pendingLateReturnFees,
        long openSupportCases,
        long pendingHostPayouts,
        long heldHostPayouts,
        long openDisputes,
        long paymentVoidRetries,
        long totalOpenItems
) {
}

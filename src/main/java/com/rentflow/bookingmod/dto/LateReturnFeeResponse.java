package com.rentflow.bookingmod.dto;

import com.rentflow.bookingmod.entity.LateReturnFee;
import com.rentflow.bookingmod.entity.LateReturnFeeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LateReturnFeeResponse(
        UUID id,
        UUID bookingId,
        LateReturnFeeStatus status,
        Instant detectedAt,
        LocalDate expectedReturnDate,
        Instant actualCheckoutAt,
        Integer daysLate,
        BigDecimal feeAmount,
        String currency,
        UUID waivedBy,
        String waiverReason
) {
    public static LateReturnFeeResponse from(LateReturnFee fee) {
        return new LateReturnFeeResponse(
                fee.getId(),
                fee.getBookingId(),
                fee.getStatus(),
                fee.getDetectedAt(),
                fee.getExpectedReturnDate(),
                fee.getActualCheckoutAt(),
                fee.getDaysLate(),
                fee.getFeeAmount(),
                fee.getCurrency(),
                fee.getWaivedBy(),
                fee.getWaiverReason());
    }
}

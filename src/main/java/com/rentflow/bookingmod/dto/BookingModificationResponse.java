package com.rentflow.bookingmod.dto;

import com.rentflow.bookingmod.entity.BookingModificationRequest;
import com.rentflow.bookingmod.entity.BookingModificationStatus;
import com.rentflow.bookingmod.entity.BookingModificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingModificationResponse(
        UUID id,
        UUID bookingId,
        UUID requesterId,
        String requesterRole,
        BookingModificationType type,
        BookingModificationStatus status,
        LocalDate currentPickupDate,
        LocalDate currentReturnDate,
        LocalDate requestedPickupDate,
        LocalDate requestedReturnDate,
        String currentPickupLocation,
        String currentReturnLocation,
        String requestedPickupLocation,
        String requestedReturnLocation,
        BigDecimal priceDelta,
        BigDecimal feeAmount,
        String currency,
        String reason,
        String hostResponseNote,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt
) {
    public static BookingModificationResponse from(BookingModificationRequest request) {
        return new BookingModificationResponse(
                request.getId(),
                request.getBookingId(),
                request.getRequesterId(),
                request.getRequesterRole(),
                request.getType(),
                request.getStatus(),
                request.getCurrentPickupDate(),
                request.getCurrentReturnDate(),
                request.getRequestedPickupDate(),
                request.getRequestedReturnDate(),
                request.getCurrentPickupLocation(),
                request.getCurrentReturnLocation(),
                request.getRequestedPickupLocation(),
                request.getRequestedReturnLocation(),
                request.getPriceDelta(),
                request.getFeeAmount(),
                request.getCurrency(),
                request.getReason(),
                request.getHostResponseNote(),
                request.getExpiresAt(),
                request.getDecidedAt(),
                request.getCreatedAt());
    }
}

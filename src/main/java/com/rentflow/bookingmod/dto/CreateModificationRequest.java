package com.rentflow.bookingmod.dto;

import com.rentflow.bookingmod.entity.BookingModificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateModificationRequest(
        @NotNull BookingModificationType type,
        LocalDate requestedPickupDate,
        LocalDate requestedReturnDate,
        @Size(max = 1000) String requestedPickupLocation,
        @Size(max = 1000) String requestedReturnLocation,
        @Size(max = 1000) String reason
) {
}

package com.rentflow.bookingmod.dto;

import jakarta.validation.constraints.Size;

public record WaiveLateReturnFeeRequest(@Size(max = 1000) String reason) {
}

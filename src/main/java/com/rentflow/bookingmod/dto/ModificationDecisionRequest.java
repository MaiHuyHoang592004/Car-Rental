package com.rentflow.bookingmod.dto;

import jakarta.validation.constraints.Size;

public record ModificationDecisionRequest(@Size(max = 1000) String note) {
}

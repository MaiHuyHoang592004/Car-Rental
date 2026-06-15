package com.rentflow.payout.dto;

import jakarta.validation.constraints.Size;

public record HostPayoutTransitionRequest(
        @Size(max = 2000) String note,
        @Size(max = 2000) String holdReason
) {
}

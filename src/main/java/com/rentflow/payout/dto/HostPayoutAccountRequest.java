package com.rentflow.payout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HostPayoutAccountRequest(
        @NotBlank @Size(max = 160) String accountHolderName,
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Pattern(regexp = "^[0-9]{4}$") String accountLast4
) {
}

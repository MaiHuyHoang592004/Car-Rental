package com.rentflow.damage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondDamageClaimRequest(
        @NotBlank @Size(max = 4000) String response
) {
}

package com.rentflow.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportCaseMessageRequest(
        @NotBlank @Size(max = 5000) String body,
        Boolean internalNote
) {
}

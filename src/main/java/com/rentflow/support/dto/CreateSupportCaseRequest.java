package com.rentflow.support.dto;

import com.rentflow.support.entity.SupportCaseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportCaseRequest(
        SupportCaseCategory category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String body
) {
}

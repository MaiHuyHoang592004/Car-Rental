package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripDamageSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DamageItemRequest(
        @NotBlank @Size(max = 80) String location,
        @NotNull TripDamageSeverity severity,
        @NotBlank @Size(max = 2000) String description,
        UUID photoFileId,
        Boolean preExisting
) {
}

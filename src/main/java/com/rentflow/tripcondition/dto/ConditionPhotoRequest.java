package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripConditionPhotoAngle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConditionPhotoRequest(
        @NotNull UUID fileId,
        @NotNull TripConditionPhotoAngle angle,
        @Min(0) Integer displayOrder,
        @Size(max = 500) String note
) {
}

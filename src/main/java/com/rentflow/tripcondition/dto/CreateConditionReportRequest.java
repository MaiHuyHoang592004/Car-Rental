package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripConditionReportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateConditionReportRequest(
        @NotNull TripConditionReportType reportType,
        @NotNull @PositiveOrZero Integer odometer,
        @NotNull @Min(0) @Max(100) Integer fuelLevel,
        @Size(max = 30) String exteriorCleanliness,
        @Size(max = 30) String interiorCleanliness,
        Boolean hasVisibleDamage,
        @Size(max = 1000) String note,
        @DecimalMin("-90.000000") @DecimalMax("90.000000") BigDecimal latitude,
        @DecimalMin("-180.000000") @DecimalMax("180.000000") BigDecimal longitude,
        @NotNull @Size(min = 4, max = 30) List<@Valid ConditionPhotoRequest> photos,
        @Size(max = 30) List<@Valid DamageItemRequest> damageItems
) {
}

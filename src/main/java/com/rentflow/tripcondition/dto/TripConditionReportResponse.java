package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripConditionReporterRole;
import com.rentflow.tripcondition.entity.TripConditionReportType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripConditionReportResponse(
        UUID id,
        UUID bookingId,
        UUID tripRecordId,
        UUID reporterUserId,
        TripConditionReporterRole reporterRole,
        TripConditionReportType reportType,
        Integer odometer,
        Integer fuelLevel,
        String exteriorCleanliness,
        String interiorCleanliness,
        boolean hasVisibleDamage,
        String note,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant submittedAt,
        List<TripConditionPhotoResponse> photos,
        List<TripDamageItemResponse> damageItems
) {
}

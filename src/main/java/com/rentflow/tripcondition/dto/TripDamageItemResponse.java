package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripDamageSeverity;

import java.util.UUID;

public record TripDamageItemResponse(
        UUID id,
        String location,
        TripDamageSeverity severity,
        String description,
        UUID photoId,
        boolean preExisting
) {
}

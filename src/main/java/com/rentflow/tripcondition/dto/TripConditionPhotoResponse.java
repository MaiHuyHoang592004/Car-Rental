package com.rentflow.tripcondition.dto;

import com.rentflow.tripcondition.entity.TripConditionPhotoAngle;

import java.time.Instant;
import java.util.UUID;

public record TripConditionPhotoResponse(
        UUID id,
        UUID fileId,
        TripConditionPhotoAngle angle,
        Integer displayOrder,
        String note,
        String signedUrl,
        Instant signedUrlExpiresAt
) {
}

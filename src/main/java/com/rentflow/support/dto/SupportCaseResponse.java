package com.rentflow.support.dto;

import com.rentflow.support.entity.SupportCase;
import com.rentflow.support.entity.SupportCaseCategory;
import com.rentflow.support.entity.SupportCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportCaseResponse(
        UUID id,
        UUID bookingId,
        UUID customerId,
        UUID hostId,
        UUID openedByUserId,
        SupportCaseCategory category,
        SupportCaseStatus status,
        String subject,
        Instant closedAt,
        UUID closedBy,
        Instant createdAt,
        List<SupportCaseMessageResponse> messages
) {
    public static SupportCaseResponse from(SupportCase supportCase, List<SupportCaseMessageResponse> messages) {
        return new SupportCaseResponse(
                supportCase.getId(),
                supportCase.getBookingId(),
                supportCase.getCustomerId(),
                supportCase.getHostId(),
                supportCase.getOpenedByUserId(),
                supportCase.getCategory(),
                supportCase.getStatus(),
                supportCase.getSubject(),
                supportCase.getClosedAt(),
                supportCase.getClosedBy(),
                supportCase.getCreatedAt(),
                messages);
    }
}

package com.rentflow.review.dto;

import com.rentflow.review.entity.HostCustomerReview;

import java.time.Instant;
import java.util.UUID;

public record HostCustomerReviewResponse(
        UUID id,
        UUID bookingId,
        UUID hostId,
        UUID customerId,
        Integer rating,
        String content,
        Instant createdAt
) {
    public static HostCustomerReviewResponse from(HostCustomerReview review) {
        return new HostCustomerReviewResponse(
                review.getId(),
                review.getBookingId(),
                review.getHostId(),
                review.getCustomerId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }
}

package com.rentflow.rentaldocument.dto;

import com.rentflow.rentaldocument.entity.RentalDocument;
import com.rentflow.rentaldocument.entity.RentalDocumentStatus;
import com.rentflow.rentaldocument.entity.RentalDocumentType;

import java.time.Instant;
import java.util.UUID;

public record RentalDocumentResponse(
        UUID id,
        UUID bookingId,
        RentalDocumentType type,
        RentalDocumentStatus status,
        String title,
        String htmlContent,
        String sourceEntityType,
        UUID sourceEntityId,
        UUID generatedBy,
        Instant generatedAt,
        Instant createdAt
) {
    public static RentalDocumentResponse from(RentalDocument document) {
        return new RentalDocumentResponse(
                document.getId(),
                document.getBookingId(),
                document.getType(),
                document.getStatus(),
                document.getTitle(),
                document.getHtmlContent(),
                document.getSourceEntityType(),
                document.getSourceEntityId(),
                document.getGeneratedBy(),
                document.getGeneratedAt(),
                document.getCreatedAt());
    }
}

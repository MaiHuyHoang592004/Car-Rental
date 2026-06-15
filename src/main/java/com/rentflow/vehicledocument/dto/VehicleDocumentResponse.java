package com.rentflow.vehicledocument.dto;

import com.rentflow.vehicledocument.entity.VehicleDocument;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.entity.VehicleDocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleDocumentResponse(
        UUID id,
        UUID vehicleId,
        UUID hostId,
        VehicleDocumentType type,
        VehicleDocumentStatus status,
        UUID fileId,
        String documentNumber,
        LocalDate issuedAt,
        LocalDate expiresAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt
) {
    public static VehicleDocumentResponse from(VehicleDocument document) {
        return new VehicleDocumentResponse(
                document.getId(),
                document.getVehicleId(),
                document.getHostId(),
                document.getType(),
                document.getStatus(),
                document.getFileId(),
                document.getDocumentNumber(),
                document.getIssuedAt(),
                document.getExpiresAt(),
                document.getReviewedBy(),
                document.getReviewedAt(),
                document.getRejectionReason(),
                document.getCreatedAt());
    }
}

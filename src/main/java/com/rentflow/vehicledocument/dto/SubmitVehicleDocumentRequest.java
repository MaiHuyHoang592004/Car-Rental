package com.rentflow.vehicledocument.dto;

import com.rentflow.vehicledocument.entity.VehicleDocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SubmitVehicleDocumentRequest(
        @NotNull VehicleDocumentType type,
        @NotNull UUID fileId,
        @Size(max = 120) String documentNumber,
        LocalDate issuedAt,
        @NotNull LocalDate expiresAt
) {
}

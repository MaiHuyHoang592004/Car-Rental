package com.rentflow.rentaldocument.dto;

import com.rentflow.rentaldocument.entity.RentalDocumentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateRentalDocumentRequest(
        @NotNull RentalDocumentType type,
        UUID sourceEntityId
) {
}

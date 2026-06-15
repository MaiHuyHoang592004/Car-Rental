package com.rentflow.vehicledocument.dto;

import jakarta.validation.constraints.Size;

public record ReviewVehicleDocumentRequest(@Size(max = 2000) String reason) {
}

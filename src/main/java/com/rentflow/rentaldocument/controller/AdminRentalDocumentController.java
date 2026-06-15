package com.rentflow.rentaldocument.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.rentaldocument.dto.GenerateRentalDocumentRequest;
import com.rentflow.rentaldocument.dto.RentalDocumentResponse;
import com.rentflow.rentaldocument.entity.RentalDocumentType;
import com.rentflow.rentaldocument.service.RentalDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRentalDocumentController {

    private final RentalDocumentService rentalDocumentService;

    @GetMapping("/rental-documents")
    public ResponseEntity<PageResponse<RentalDocumentResponse>> list(
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(rentalDocumentService.listAdmin(bookingId, parseType(type), pageable));
    }

    @PostMapping("/bookings/{bookingId}/documents")
    public ResponseEntity<RentalDocumentResponse> generate(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GenerateRentalDocumentRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rentalDocumentService.generate(bookingId, idempotencyKey, request));
    }

    private RentalDocumentType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return RentalDocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid rental document type: " + type);
        }
    }
}

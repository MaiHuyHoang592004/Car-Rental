package com.rentflow.rentaldocument.controller;

import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.rentaldocument.dto.GenerateRentalDocumentRequest;
import com.rentflow.rentaldocument.dto.RentalDocumentResponse;
import com.rentflow.rentaldocument.service.RentalDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RentalDocumentController {

    private final RentalDocumentService rentalDocumentService;

    @PostMapping("/api/v1/bookings/{bookingId}/documents")
    public ResponseEntity<RentalDocumentResponse> generate(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GenerateRentalDocumentRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rentalDocumentService.generate(bookingId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/documents")
    public ResponseEntity<PageResponse<RentalDocumentResponse>> listForBooking(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(rentalDocumentService.listForBooking(bookingId, pageable));
    }

    @GetMapping("/api/v1/rental-documents/{documentId}")
    public ResponseEntity<RentalDocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(rentalDocumentService.get(documentId));
    }

    @GetMapping("/api/v1/rental-documents/{documentId}/print")
    public ResponseEntity<String> print(@PathVariable UUID documentId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(rentalDocumentService.printHtml(documentId));
    }
}

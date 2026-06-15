package com.rentflow.bookingmod.controller;

import com.rentflow.bookingmod.dto.BookingModificationResponse;
import com.rentflow.bookingmod.dto.CreateModificationRequest;
import com.rentflow.bookingmod.dto.ModificationPreviewResponse;
import com.rentflow.bookingmod.service.BookingModificationService;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
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
@RequiredArgsConstructor
public class BookingModificationController {

    private final BookingModificationService bookingModificationService;

    @PostMapping("/api/v1/bookings/{bookingId}/modification-preview")
    public ResponseEntity<ModificationPreviewResponse> preview(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CreateModificationRequest request) {
        return ResponseEntity.ok(bookingModificationService.preview(bookingId, request));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/modification-requests")
    public ResponseEntity<BookingModificationResponse> create(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateModificationRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingModificationService.create(bookingId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/modification-requests")
    public ResponseEntity<PageResponse<BookingModificationResponse>> listForBooking(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bookingModificationService.listForBooking(bookingId, pageable));
    }

    @PostMapping("/api/v1/booking-modification-requests/{requestId}/cancel")
    public ResponseEntity<BookingModificationResponse> cancel(
            @PathVariable UUID requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(bookingModificationService.cancel(requestId, idempotencyKey));
    }
}

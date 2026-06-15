package com.rentflow.bookingmod.controller;

import com.rentflow.bookingmod.dto.BookingModificationResponse;
import com.rentflow.bookingmod.dto.ModificationDecisionRequest;
import com.rentflow.bookingmod.entity.BookingModificationStatus;
import com.rentflow.bookingmod.service.BookingModificationService;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/host/booking-modification-requests")
@RequiredArgsConstructor
public class HostBookingModificationController {

    private final BookingModificationService bookingModificationService;

    @GetMapping
    public ResponseEntity<PageResponse<BookingModificationResponse>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bookingModificationService.listHostRequests(parseStatus(status), pageable));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<BookingModificationResponse> approve(
            @PathVariable UUID requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ModificationDecisionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(bookingModificationService.approve(requestId, idempotencyKey, request));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<BookingModificationResponse> reject(
            @PathVariable UUID requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ModificationDecisionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(bookingModificationService.reject(requestId, idempotencyKey, request));
    }

    private BookingModificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingModificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid booking modification status: " + status);
        }
    }
}

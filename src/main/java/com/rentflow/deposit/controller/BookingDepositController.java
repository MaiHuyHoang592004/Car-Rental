package com.rentflow.deposit.controller;

import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.deposit.dto.DepositResponse;
import com.rentflow.deposit.service.DepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/deposit")
@RequiredArgsConstructor
public class BookingDepositController {

    private final DepositService depositService;

    @GetMapping
    public ResponseEntity<DepositResponse> getDeposit(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(depositService.getBookingDeposit(bookingId));
    }

    @PostMapping("/authorize")
    public ResponseEntity<DepositResponse> authorize(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(depositService.authorize(bookingId, idempotencyKey));
    }
}

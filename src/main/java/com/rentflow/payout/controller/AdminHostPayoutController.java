package com.rentflow.payout.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.payout.dto.HostPayoutQueueResponse;
import com.rentflow.payout.dto.HostPayoutResponse;
import com.rentflow.payout.dto.HostPayoutTransitionRequest;
import com.rentflow.payout.entity.HostPayoutStatus;
import com.rentflow.payout.service.HostPayoutService;
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
@RequestMapping("/api/v1/admin/host-payouts")
@RequiredArgsConstructor
public class AdminHostPayoutController {

    private final HostPayoutService hostPayoutService;

    @GetMapping
    public ResponseEntity<PageResponse<HostPayoutResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(hostPayoutService.listAdminPayouts(parseStatus(status), pageable));
    }

    @PostMapping("/process-queue")
    public ResponseEntity<HostPayoutQueueResponse> processQueue(@RequestParam(defaultValue = "100") int batchSize) {
        return ResponseEntity.ok(new HostPayoutQueueResponse(hostPayoutService.createPayoutQueue(batchSize)));
    }

    @PostMapping("/{payoutId}/approve")
    public ResponseEntity<HostPayoutResponse> approve(
            @PathVariable UUID payoutId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) HostPayoutTransitionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(hostPayoutService.approve(payoutId, idempotencyKey, effective(request)));
    }

    @PostMapping("/{payoutId}/hold")
    public ResponseEntity<HostPayoutResponse> hold(
            @PathVariable UUID payoutId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) HostPayoutTransitionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(hostPayoutService.hold(payoutId, idempotencyKey, effective(request)));
    }

    @PostMapping("/{payoutId}/mark-paid")
    public ResponseEntity<HostPayoutResponse> markPaid(
            @PathVariable UUID payoutId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) HostPayoutTransitionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(hostPayoutService.markPaid(payoutId, idempotencyKey, effective(request)));
    }

    @PostMapping("/{payoutId}/fail")
    public ResponseEntity<HostPayoutResponse> fail(
            @PathVariable UUID payoutId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) HostPayoutTransitionRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(hostPayoutService.fail(payoutId, idempotencyKey, effective(request)));
    }

    private HostPayoutTransitionRequest effective(HostPayoutTransitionRequest request) {
        return request == null ? new HostPayoutTransitionRequest(null, null) : request;
    }

    private HostPayoutStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return HostPayoutStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid host payout status: " + status);
        }
    }
}

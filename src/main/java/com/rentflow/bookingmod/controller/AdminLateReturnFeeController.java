package com.rentflow.bookingmod.controller;

import com.rentflow.bookingmod.dto.LateReturnFeeResponse;
import com.rentflow.bookingmod.dto.WaiveLateReturnFeeRequest;
import com.rentflow.bookingmod.entity.LateReturnFeeStatus;
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
@RequestMapping("/api/v1/admin/late-return-fees")
@RequiredArgsConstructor
public class AdminLateReturnFeeController {

    private final BookingModificationService bookingModificationService;

    @GetMapping
    public ResponseEntity<PageResponse<LateReturnFeeResponse>> listFees(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bookingModificationService.listLateFees(parseStatus(status), pageable));
    }

    @PostMapping("/{feeId}/waive")
    public ResponseEntity<LateReturnFeeResponse> waive(
            @PathVariable UUID feeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WaiveLateReturnFeeRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(bookingModificationService.waiveLateFee(feeId, idempotencyKey, request));
    }

    @PostMapping("/{feeId}/charge")
    public ResponseEntity<LateReturnFeeResponse> charge(
            @PathVariable UUID feeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(bookingModificationService.chargeLateFee(feeId, idempotencyKey));
    }

    private LateReturnFeeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return LateReturnFeeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid late return fee status: " + status);
        }
    }
}

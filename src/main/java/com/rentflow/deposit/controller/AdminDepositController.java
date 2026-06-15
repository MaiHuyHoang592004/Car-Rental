package com.rentflow.deposit.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.deposit.dto.DeductDepositRequest;
import com.rentflow.deposit.dto.DepositResponse;
import com.rentflow.deposit.entity.DepositStatus;
import com.rentflow.deposit.service.DepositService;
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
@RequestMapping("/api/v1/admin/deposits")
@RequiredArgsConstructor
public class AdminDepositController {

    private final DepositService depositService;

    @GetMapping
    public ResponseEntity<PageResponse<DepositResponse>> listDeposits(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(depositService.listAdminDeposits(parseStatus(status), pageable));
    }

    @PostMapping("/{depositId}/deduct")
    public ResponseEntity<DepositResponse> deduct(
            @PathVariable UUID depositId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DeductDepositRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(depositService.deduct(depositId, idempotencyKey, request));
    }

    @PostMapping("/{depositId}/release")
    public ResponseEntity<DepositResponse> release(
            @PathVariable UUID depositId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(depositService.release(depositId, idempotencyKey));
    }

    private DepositStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DepositStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid deposit status: " + status);
        }
    }
}

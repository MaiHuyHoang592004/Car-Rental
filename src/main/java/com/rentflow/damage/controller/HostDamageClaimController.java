package com.rentflow.damage.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.damage.dto.CreateDamageClaimRequest;
import com.rentflow.damage.dto.DamageClaimResponse;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.service.DamageClaimService;
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
@RequestMapping("/api/v1/host")
@RequiredArgsConstructor
public class HostDamageClaimController {

    private final DamageClaimService damageClaimService;

    @PostMapping("/bookings/{bookingId}/damage-claims")
    public ResponseEntity<DamageClaimResponse> createClaim(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateDamageClaimRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(damageClaimService.createClaim(bookingId, idempotencyKey, request));
    }

    @GetMapping("/damage-claims")
    public ResponseEntity<PageResponse<DamageClaimResponse>> listClaims(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(damageClaimService.listHostClaims(parseStatus(status), pageable));
    }

    @GetMapping("/damage-claims/{claimId}")
    public ResponseEntity<DamageClaimResponse> getClaim(@PathVariable UUID claimId) {
        return ResponseEntity.ok(damageClaimService.getHostClaim(claimId));
    }

    private DamageClaimStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DamageClaimStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid damage claim status: " + status);
        }
    }
}

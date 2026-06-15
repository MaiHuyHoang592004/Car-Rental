package com.rentflow.damage.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.damage.dto.DamageClaimResponse;
import com.rentflow.damage.dto.ResolveDamageClaimRequest;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.service.DamageClaimService;
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
@RequestMapping("/api/v1/admin/damage-claims")
@RequiredArgsConstructor
public class AdminDamageClaimController {

    private final DamageClaimService damageClaimService;

    @GetMapping
    public ResponseEntity<PageResponse<DamageClaimResponse>> listClaims(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(damageClaimService.listAdminClaims(parseStatus(status), pageable));
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<DamageClaimResponse> getClaim(@PathVariable UUID claimId) {
        return ResponseEntity.ok(damageClaimService.getAdminClaim(claimId));
    }

    @PostMapping("/{claimId}/approve")
    public ResponseEntity<DamageClaimResponse> approve(
            @PathVariable UUID claimId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ResolveDamageClaimRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        ResolveDamageClaimRequest effective = request == null ? new ResolveDamageClaimRequest(null, null) : request;
        return ResponseEntity.ok(damageClaimService.approve(claimId, idempotencyKey, effective));
    }

    @PostMapping("/{claimId}/reject")
    public ResponseEntity<DamageClaimResponse> reject(
            @PathVariable UUID claimId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) ResolveDamageClaimRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        ResolveDamageClaimRequest effective = request == null ? new ResolveDamageClaimRequest(null, null) : request;
        return ResponseEntity.ok(damageClaimService.reject(claimId, idempotencyKey, effective));
    }

    @PostMapping("/{claimId}/charge")
    public ResponseEntity<DamageClaimResponse> charge(
            @PathVariable UUID claimId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(damageClaimService.charge(claimId, idempotencyKey));
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

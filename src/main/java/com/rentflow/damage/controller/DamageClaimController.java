package com.rentflow.damage.controller;

import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.damage.dto.DamageClaimResponse;
import com.rentflow.damage.dto.RespondDamageClaimRequest;
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
@RequiredArgsConstructor
public class DamageClaimController {

    private final DamageClaimService damageClaimService;

    @GetMapping("/api/v1/bookings/{bookingId}/damage-claims")
    public ResponseEntity<PageResponse<DamageClaimResponse>> listBookingClaims(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(damageClaimService.listBookingClaims(bookingId, pageable));
    }

    @PostMapping("/api/v1/damage-claims/{claimId}/respond")
    public ResponseEntity<DamageClaimResponse> respond(
            @PathVariable UUID claimId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RespondDamageClaimRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(damageClaimService.respond(claimId, idempotencyKey, request));
    }
}

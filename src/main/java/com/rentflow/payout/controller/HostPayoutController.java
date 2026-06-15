package com.rentflow.payout.controller;

import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.payout.dto.HostPayoutAccountRequest;
import com.rentflow.payout.dto.HostPayoutAccountResponse;
import com.rentflow.payout.dto.HostPayoutResponse;
import com.rentflow.payout.service.HostPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/host")
@RequiredArgsConstructor
public class HostPayoutController {

    private final HostPayoutService hostPayoutService;

    @GetMapping("/payout-account")
    public ResponseEntity<HostPayoutAccountResponse> getAccount() {
        return ResponseEntity.ok(hostPayoutService.getMyAccount());
    }

    @PutMapping("/payout-account")
    public ResponseEntity<HostPayoutAccountResponse> upsertAccount(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody HostPayoutAccountRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(hostPayoutService.upsertAccount(idempotencyKey, request));
    }

    @GetMapping("/payouts")
    public ResponseEntity<PageResponse<HostPayoutResponse>> listPayouts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(hostPayoutService.listMyPayouts(pageable));
    }
}

package com.rentflow.support.controller;

import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.support.dto.CreateSupportCaseMessageRequest;
import com.rentflow.support.dto.CreateSupportCaseRequest;
import com.rentflow.support.dto.SupportCaseResponse;
import com.rentflow.support.service.SupportCaseService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SupportCaseController {

    private final SupportCaseService supportCaseService;

    @PostMapping("/api/v1/bookings/{bookingId}/support-cases")
    public ResponseEntity<SupportCaseResponse> create(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSupportCaseRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportCaseService.create(bookingId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/support-cases")
    public ResponseEntity<PageResponse<SupportCaseResponse>> listBookingCases(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(supportCaseService.listBookingCases(bookingId, pageable));
    }

    @GetMapping("/api/v1/support-cases")
    public ResponseEntity<PageResponse<SupportCaseResponse>> listMyCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(supportCaseService.listMyCases(pageable));
    }

    @GetMapping("/api/v1/support-cases/{caseId}")
    public ResponseEntity<SupportCaseResponse> get(@PathVariable UUID caseId) {
        return ResponseEntity.ok(supportCaseService.get(caseId));
    }

    @PostMapping("/api/v1/support-cases/{caseId}/messages")
    public ResponseEntity<SupportCaseResponse> addMessage(
            @PathVariable UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSupportCaseMessageRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(supportCaseService.addMessage(caseId, idempotencyKey, request));
    }
}

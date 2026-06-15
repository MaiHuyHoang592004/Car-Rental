package com.rentflow.support.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.IdempotencyKeyValidator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.support.dto.CloseSupportCaseRequest;
import com.rentflow.support.dto.CreateSupportCaseMessageRequest;
import com.rentflow.support.dto.SupportCaseResponse;
import com.rentflow.support.entity.SupportCaseStatus;
import com.rentflow.support.service.SupportCaseService;
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
@RequestMapping("/api/v1/admin/support-cases")
@RequiredArgsConstructor
public class AdminSupportCaseController {

    private final SupportCaseService supportCaseService;

    @GetMapping
    public ResponseEntity<PageResponse<SupportCaseResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(supportCaseService.listAdminCases(parseStatus(status), pageable));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<SupportCaseResponse> get(@PathVariable UUID caseId) {
        return ResponseEntity.ok(supportCaseService.get(caseId));
    }

    @PostMapping("/{caseId}/messages")
    public ResponseEntity<SupportCaseResponse> addMessage(
            @PathVariable UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSupportCaseMessageRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(supportCaseService.addMessage(caseId, idempotencyKey, request));
    }

    @PostMapping("/{caseId}/close")
    public ResponseEntity<SupportCaseResponse> close(
            @PathVariable UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) CloseSupportCaseRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        CloseSupportCaseRequest effective = request == null ? new CloseSupportCaseRequest(null) : request;
        return ResponseEntity.ok(supportCaseService.close(caseId, idempotencyKey, effective));
    }

    private SupportCaseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SupportCaseStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid support case status: " + status);
        }
    }
}

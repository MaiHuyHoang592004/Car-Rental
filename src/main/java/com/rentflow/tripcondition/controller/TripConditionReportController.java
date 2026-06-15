package com.rentflow.tripcondition.controller;

import com.rentflow.common.exception.IdempotencyKeyRequiredException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.tripcondition.dto.CreateConditionReportRequest;
import com.rentflow.tripcondition.dto.TripConditionReportResponse;
import com.rentflow.tripcondition.service.TripConditionReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
@RequiredArgsConstructor
public class TripConditionReportController {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final TripConditionReportService tripConditionReportService;

    @PostMapping("/trip-photos/upload-intent")
    public ResponseEntity<FileUploadIntentResponse> createTripPhotoUploadIntent(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePhotoUploadIntentRequest request) {
        validateIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(tripConditionReportService.createTripPhotoUploadIntent(
                bookingId,
                idempotencyKey,
                request));
    }

    @PostMapping("/condition-reports")
    public ResponseEntity<TripConditionReportResponse> createReport(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateConditionReportRequest request) {
        validateIdempotencyKey(idempotencyKey);
        TripConditionReportResponse response = tripConditionReportService.createReport(
                bookingId,
                idempotencyKey,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/condition-reports")
    public ResponseEntity<List<TripConditionReportResponse>> listReports(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(tripConditionReportService.listReports(bookingId));
    }

    @GetMapping("/condition-reports/{reportId}")
    public ResponseEntity<TripConditionReportResponse> getReport(
            @PathVariable UUID bookingId,
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(tripConditionReportService.getReport(bookingId, reportId));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be a UUID-v4 value");
        }
    }
}

package com.rentflow.review.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.review.dto.CreateHostCustomerReviewRequest;
import com.rentflow.review.dto.HostCustomerReviewResponse;
import com.rentflow.review.service.HostCustomerReviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HostCustomerReviewController {

    private final HostCustomerReviewService hostCustomerReviewService;

    @PostMapping("/api/v1/host/bookings/{bookingId}/customer-review")
    public ResponseEntity<HostCustomerReviewResponse> create(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CreateHostCustomerReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hostCustomerReviewService.create(bookingId, request));
    }

    @GetMapping("/api/v1/host/customer-reviews")
    public ResponseEntity<PageResponse<HostCustomerReviewResponse>> listHostSubmittedReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(hostCustomerReviewService.listMySubmittedReviews(pageable));
    }

    @GetMapping("/api/v1/me/host-reviews")
    public ResponseEntity<PageResponse<HostCustomerReviewResponse>> listMyHostReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(hostCustomerReviewService.listMyCustomerReviews(pageable));
    }

    @GetMapping("/api/v1/admin/users/{userId}/host-reviews")
    public ResponseEntity<PageResponse<HostCustomerReviewResponse>> listAdminUserHostReviews(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(hostCustomerReviewService.listAdminUserReviews(userId, pageable));
    }
}

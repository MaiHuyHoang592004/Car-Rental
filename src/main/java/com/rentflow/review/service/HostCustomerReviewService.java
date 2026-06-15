package com.rentflow.review.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.review.dto.CreateHostCustomerReviewRequest;
import com.rentflow.review.dto.HostCustomerReviewResponse;
import com.rentflow.review.entity.HostCustomerReview;
import com.rentflow.review.repository.HostCustomerReviewRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HostCustomerReviewService {

    private final HostCustomerReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final SecurityContext securityContext;

    public HostCustomerReviewService(
            HostCustomerReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            SecurityContext securityContext) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.securityContext = securityContext;
    }

    @Transactional
    public HostCustomerReviewResponse create(UUID bookingId, CreateHostCustomerReviewRequest request) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getHostId().equals(hostId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Host customer review requires COMPLETED booking");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessRuleException("HOST_CUSTOMER_REVIEW_ALREADY_EXISTS", "Host customer review already exists");
        }
        HostCustomerReview review = new HostCustomerReview();
        review.setBookingId(bookingId);
        review.setHostId(hostId);
        review.setCustomerId(booking.getCustomerId());
        review.setRating(request.rating());
        review.setContent(normalize(request.content()));
        review = reviewRepository.save(review);
        return HostCustomerReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<HostCustomerReviewResponse> listMyCustomerReviews(Pageable pageable) {
        UUID customerId = securityContext.currentUserId();
        return PageResponse.from(reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable),
                HostCustomerReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<HostCustomerReviewResponse> listAdminUserReviews(UUID customerId, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        return PageResponse.from(reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable),
                HostCustomerReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<HostCustomerReviewResponse> listMySubmittedReviews(Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        return PageResponse.from(reviewRepository.findByHostIdOrderByCreatedAtDesc(hostId, pageable),
                HostCustomerReviewResponse::from);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

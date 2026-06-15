package com.rentflow.review.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.review.dto.CreateHostCustomerReviewRequest;
import com.rentflow.review.dto.HostCustomerReviewResponse;
import com.rentflow.review.entity.HostCustomerReview;
import com.rentflow.review.repository.HostCustomerReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostCustomerReviewServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-4777-9777-777777777777");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID REVIEW_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");

    @Mock private HostCustomerReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SecurityContext securityContext;

    private HostCustomerReviewService service;

    @BeforeEach
    void setUp() {
        service = new HostCustomerReviewService(reviewRepository, bookingRepository, securityContext);
    }

    @Test
    void hostCreatesCustomerReviewForCompletedBooking() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatus.COMPLETED)));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.save(any(HostCustomerReview.class))).thenAnswer(invocation -> {
            HostCustomerReview review = invocation.getArgument(0);
            review.setId(REVIEW_ID);
            return review;
        });

        HostCustomerReviewResponse response = service.create(
                BOOKING_ID,
                new CreateHostCustomerReviewRequest(5, "Great customer"));

        verify(securityContext).requireRole(Role.HOST);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.rating()).isEqualTo(5);
    }

    @Test
    void rejectsNonCompletedBooking() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.create(
                BOOKING_ID,
                new CreateHostCustomerReviewRequest(5, "Great customer")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void rejectsDuplicateReview() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatus.COMPLETED)));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                BOOKING_ID,
                new CreateHostCustomerReviewRequest(5, "Great customer")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void adminListsUserReviews() {
        HostCustomerReview review = review();
        when(reviewRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));

        var response = service.listAdminUserReviews(CUSTOMER_ID, PageRequest.of(0, 20));

        verify(securityContext).requireRole(Role.ADMIN);
        assertThat(response.content()).hasSize(1);
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setHostId(HOST_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setStatus(status);
        return booking;
    }

    private HostCustomerReview review() {
        HostCustomerReview review = new HostCustomerReview();
        review.setId(REVIEW_ID);
        review.setBookingId(BOOKING_ID);
        review.setHostId(HOST_ID);
        review.setCustomerId(CUSTOMER_ID);
        review.setRating(5);
        review.setContent("Great customer");
        return review;
    }
}

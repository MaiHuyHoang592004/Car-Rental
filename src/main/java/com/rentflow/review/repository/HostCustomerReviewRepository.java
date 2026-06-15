package com.rentflow.review.repository;

import com.rentflow.review.entity.HostCustomerReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HostCustomerReviewRepository extends JpaRepository<HostCustomerReview, UUID> {

    boolean existsByBookingId(UUID bookingId);

    Page<HostCustomerReview> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<HostCustomerReview> findByHostIdOrderByCreatedAtDesc(UUID hostId, Pageable pageable);
}

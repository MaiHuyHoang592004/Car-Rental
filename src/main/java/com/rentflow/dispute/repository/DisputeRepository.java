package com.rentflow.dispute.repository;

import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    boolean existsByBookingIdAndCustomerId(UUID bookingId, UUID customerId);

    Optional<Dispute> findByIdAndStatus(UUID id, DisputeStatus status);

    Page<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status, Pageable pageable);

    long countByStatus(DisputeStatus status);
}

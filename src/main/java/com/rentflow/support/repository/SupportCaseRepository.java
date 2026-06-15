package com.rentflow.support.repository;

import com.rentflow.support.entity.SupportCase;
import com.rentflow.support.entity.SupportCaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupportCaseRepository extends JpaRepository<SupportCase, UUID> {

    Page<SupportCase> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<SupportCase> findByStatusOrderByCreatedAtDesc(SupportCaseStatus status, Pageable pageable);

    long countByStatus(SupportCaseStatus status);

    Page<SupportCase> findByCustomerIdOrHostIdOrderByCreatedAtDesc(UUID customerId, UUID hostId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SupportCase c WHERE c.id = :id")
    Optional<SupportCase> findByIdForUpdate(@Param("id") UUID id);
}

package com.rentflow.payout.repository;

import com.rentflow.payout.entity.HostPayout;
import com.rentflow.payout.entity.HostPayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HostPayoutRepository extends JpaRepository<HostPayout, UUID> {

    boolean existsByBookingId(UUID bookingId);

    Page<HostPayout> findByHostIdOrderByCreatedAtDesc(UUID hostId, Pageable pageable);

    Page<HostPayout> findByStatusOrderByCreatedAtDesc(HostPayoutStatus status, Pageable pageable);

    long countByStatus(HostPayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM HostPayout p WHERE p.id = :id")
    Optional<HostPayout> findByIdForUpdate(@Param("id") UUID id);
}

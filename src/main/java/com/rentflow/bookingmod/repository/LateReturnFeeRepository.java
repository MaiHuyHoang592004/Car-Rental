package com.rentflow.bookingmod.repository;

import com.rentflow.bookingmod.entity.LateReturnFee;
import com.rentflow.bookingmod.entity.LateReturnFeeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LateReturnFeeRepository extends JpaRepository<LateReturnFee, UUID> {

    boolean existsByBookingIdAndStatus(UUID bookingId, LateReturnFeeStatus status);

    Page<LateReturnFee> findByStatusOrderByCreatedAtDesc(LateReturnFeeStatus status, Pageable pageable);

    long countByStatus(LateReturnFeeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM LateReturnFee f WHERE f.id = :id")
    Optional<LateReturnFee> findByIdForUpdate(@Param("id") UUID id);
}

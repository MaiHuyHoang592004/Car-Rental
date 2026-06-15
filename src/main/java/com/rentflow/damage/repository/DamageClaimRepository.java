package com.rentflow.damage.repository;

import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DamageClaimRepository extends JpaRepository<DamageClaim, UUID> {

    Page<DamageClaim> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<DamageClaim> findByHostIdOrderByCreatedAtDesc(UUID hostId, Pageable pageable);

    Page<DamageClaim> findByHostIdAndStatusOrderByCreatedAtDesc(
            UUID hostId,
            DamageClaimStatus status,
            Pageable pageable);

    Page<DamageClaim> findByStatusOrderByCreatedAtDesc(DamageClaimStatus status, Pageable pageable);

    long countByStatus(DamageClaimStatus status);

    Optional<DamageClaim> findByIdAndHostId(UUID id, UUID hostId);

    boolean existsByBookingIdAndStatusIn(UUID bookingId, java.util.Collection<DamageClaimStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DamageClaim c WHERE c.id = :id")
    Optional<DamageClaim> findByIdForUpdate(@Param("id") UUID id);
}

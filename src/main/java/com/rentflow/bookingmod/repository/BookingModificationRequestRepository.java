package com.rentflow.bookingmod.repository;

import com.rentflow.bookingmod.entity.BookingModificationRequest;
import com.rentflow.bookingmod.entity.BookingModificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface BookingModificationRequestRepository extends JpaRepository<BookingModificationRequest, UUID> {

    Page<BookingModificationRequest> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<BookingModificationRequest> findByStatusOrderByCreatedAtDesc(BookingModificationStatus status, Pageable pageable);

    long countByStatus(BookingModificationStatus status);

    @Query("""
            SELECT r FROM BookingModificationRequest r
            JOIN Booking b ON b.id = r.bookingId
            WHERE b.hostId = :hostId
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<BookingModificationRequest> findHostRequests(
            @Param("hostId") UUID hostId,
            @Param("status") BookingModificationStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BookingModificationRequest r WHERE r.id = :id")
    Optional<BookingModificationRequest> findByIdForUpdate(@Param("id") UUID id);
}

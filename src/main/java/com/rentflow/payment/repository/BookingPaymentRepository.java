package com.rentflow.payment.repository;

import com.rentflow.payment.entity.BookingPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPaymentRepository extends JpaRepository<BookingPayment, UUID> {

    Optional<BookingPayment> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPayment bp WHERE bp.bookingId = :bookingId")
    Optional<BookingPayment> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPayment bp WHERE bp.id = :id")
    Optional<BookingPayment> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM booking_payments
            WHERE void_retry_required = TRUE
              AND provider = 'COREBANK'
              AND (void_retry_next_at IS NULL OR void_retry_next_at <= :now)
              AND void_retry_count < :maxAttempts
            ORDER BY updated_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BookingPayment> findVoidRetryCandidatesForUpdate(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize);

    @Query("""
            SELECT COALESCE(SUM(bp.capturedAmount), 0)
            FROM BookingPayment bp
            JOIN Booking b ON b.id = bp.bookingId
            WHERE bp.capturedAmount > 0
              AND b.createdAt >= :fromInclusive
              AND b.createdAt < :toExclusive
            """)
    BigDecimal sumCapturedAmountInRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT COALESCE(COUNT(b.id), 0)
            FROM BookingPayment bp
            JOIN Booking b ON b.id = bp.bookingId
            WHERE bp.capturedAmount > 0
              AND b.createdAt >= :fromInclusive
              AND b.createdAt < :toExclusive
            """)
    long countCapturedBookingsInRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT COALESCE(SUM(bp.capturedAmount), 0)
            FROM BookingPayment bp
            JOIN Booking b ON b.id = bp.bookingId
            WHERE bp.capturedAmount > 0
              AND b.hostId = :hostId
              AND b.createdAt >= :fromInclusive
              AND b.createdAt < :toExclusive
            """)
    BigDecimal sumCapturedAmountForHostInRange(
            @Param("hostId") UUID hostId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT COALESCE(COUNT(b.id), 0)
            FROM BookingPayment bp
            JOIN Booking b ON b.id = bp.bookingId
            WHERE bp.capturedAmount > 0
              AND b.hostId = :hostId
              AND b.createdAt >= :fromInclusive
              AND b.createdAt < :toExclusive
            """)
    long countCapturedBookingsForHostInRange(
            @Param("hostId") UUID hostId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            SELECT bp.*
            FROM booking_payments bp
            JOIN bookings b ON b.id = bp.booking_id
            WHERE b.status = 'COMPLETED'
              AND bp.captured_amount > 0
              AND NOT EXISTS (
                  SELECT 1 FROM host_payouts hp WHERE hp.booking_id = bp.booking_id
              )
            ORDER BY b.updated_at ASC, bp.id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BookingPayment> findPayoutEligibleCapturedPaymentsForUpdate(@Param("batchSize") int batchSize);

    long countByVoidRetryRequiredTrue();
}

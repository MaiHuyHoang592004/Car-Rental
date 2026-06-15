package com.rentflow.deposit.repository;

import com.rentflow.deposit.entity.BookingDeposit;
import com.rentflow.deposit.entity.DepositStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookingDepositRepository extends JpaRepository<BookingDeposit, UUID> {

    Optional<BookingDeposit> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    Page<BookingDeposit> findByStatusOrderByCreatedAtDesc(DepositStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BookingDeposit d WHERE d.id = :id")
    Optional<BookingDeposit> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BookingDeposit d WHERE d.bookingId = :bookingId")
    Optional<BookingDeposit> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}

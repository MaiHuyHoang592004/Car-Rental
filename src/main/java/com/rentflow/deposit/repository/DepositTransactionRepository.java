package com.rentflow.deposit.repository;

import com.rentflow.deposit.entity.DepositTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositTransactionRepository extends JpaRepository<DepositTransaction, UUID> {

    List<DepositTransaction> findByBookingDepositIdOrderByCreatedAtDesc(UUID bookingDepositId);
}

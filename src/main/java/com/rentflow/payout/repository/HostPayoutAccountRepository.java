package com.rentflow.payout.repository;

import com.rentflow.payout.entity.HostPayoutAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HostPayoutAccountRepository extends JpaRepository<HostPayoutAccount, UUID> {

    Optional<HostPayoutAccount> findByHostId(UUID hostId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM HostPayoutAccount a WHERE a.hostId = :hostId")
    Optional<HostPayoutAccount> findByHostIdForUpdate(@Param("hostId") UUID hostId);
}

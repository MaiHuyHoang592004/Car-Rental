package com.rentflow.payout.dto;

import com.rentflow.payout.entity.HostPayoutAccount;
import com.rentflow.payout.entity.HostPayoutAccountProvider;
import com.rentflow.payout.entity.HostPayoutAccountStatus;

import java.time.Instant;
import java.util.UUID;

public record HostPayoutAccountResponse(
        UUID id,
        UUID hostId,
        HostPayoutAccountProvider provider,
        HostPayoutAccountStatus status,
        String accountHolderName,
        String bankName,
        String accountLast4,
        Instant createdAt
) {
    public static HostPayoutAccountResponse from(HostPayoutAccount account) {
        return new HostPayoutAccountResponse(
                account.getId(),
                account.getHostId(),
                account.getProvider(),
                account.getStatus(),
                account.getAccountHolderName(),
                account.getBankName(),
                account.getAccountLast4(),
                account.getCreatedAt());
    }
}

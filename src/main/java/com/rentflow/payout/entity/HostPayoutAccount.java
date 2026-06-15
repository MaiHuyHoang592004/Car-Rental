package com.rentflow.payout.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "host_payout_accounts")
@Getter
@Setter
public class HostPayoutAccount extends BaseEntity {

    @Column(name = "host_id", nullable = false, unique = true)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private HostPayoutAccountProvider provider = HostPayoutAccountProvider.MANUAL_BANK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HostPayoutAccountStatus status = HostPayoutAccountStatus.ACTIVE;

    @Column(name = "account_holder_name", nullable = false, length = 160)
    private String accountHolderName;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

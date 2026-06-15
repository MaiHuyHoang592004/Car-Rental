package com.rentflow.deposit.entity;

import com.rentflow.common.BaseEntity;
import com.rentflow.payment.entity.PaymentProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_deposits")
@Getter
@Setter
public class BookingDeposit extends BaseEntity {

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DepositStatus status = DepositStatus.PENDING_AUTHORIZATION;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "held_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal heldAmount = BigDecimal.ZERO;

    @Column(name = "released_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(name = "deducted_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "release_eligible_at")
    private Instant releaseEligibleAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProviderType provider = PaymentProviderType.STUB;

    @Column(name = "provider_hold_id", length = 120)
    private String providerHoldId;

    @Column(name = "provider_status", length = 80)
    private String providerStatus;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

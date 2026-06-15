package com.rentflow.bookingmod.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "late_return_fees")
@Getter
@Setter
public class LateReturnFee extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LateReturnFeeStatus status = LateReturnFeeStatus.PENDING;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "expected_return_date", nullable = false)
    private LocalDate expectedReturnDate;

    @Column(name = "actual_checkout_at")
    private Instant actualCheckoutAt;

    @Column(name = "days_late", nullable = false)
    private Integer daysLate;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "waived_by")
    private UUID waivedBy;

    @Column(name = "waiver_reason", columnDefinition = "TEXT")
    private String waiverReason;
}

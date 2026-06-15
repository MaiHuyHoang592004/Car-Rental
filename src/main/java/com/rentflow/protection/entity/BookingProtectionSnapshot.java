package com.rentflow.protection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_protection_snapshots")
@Getter
@Setter
public class BookingProtectionSnapshot {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "protection_plan_id")
    private UUID protectionPlanId;

    @Column(name = "plan_code", nullable = false, length = 40)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @Column(name = "plan_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal planFee;

    @Column(name = "deductible_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductibleAmount;

    @Column(name = "max_coverage_amount", precision = 12, scale = 2)
    private BigDecimal maxCoverageAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

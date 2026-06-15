package com.rentflow.bookingmod.entity;

import com.rentflow.common.BaseEntity;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_modification_requests")
@Getter
@Setter
public class BookingModificationRequest extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "requester_role", nullable = false, length = 20)
    private String requesterRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingModificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingModificationStatus status = BookingModificationStatus.PENDING_HOST_APPROVAL;

    @Column(name = "current_pickup_date", nullable = false)
    private LocalDate currentPickupDate;

    @Column(name = "current_return_date", nullable = false)
    private LocalDate currentReturnDate;

    @Column(name = "requested_pickup_date")
    private LocalDate requestedPickupDate;

    @Column(name = "requested_return_date")
    private LocalDate requestedReturnDate;

    @Column(name = "current_pickup_location", columnDefinition = "TEXT")
    private String currentPickupLocation;

    @Column(name = "current_return_location", columnDefinition = "TEXT")
    private String currentReturnLocation;

    @Column(name = "requested_pickup_location", columnDefinition = "TEXT")
    private String requestedPickupLocation;

    @Column(name = "requested_return_location", columnDefinition = "TEXT")
    private String requestedReturnLocation;

    @Column(name = "price_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "host_response_note", columnDefinition = "TEXT")
    private String hostResponseNote;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

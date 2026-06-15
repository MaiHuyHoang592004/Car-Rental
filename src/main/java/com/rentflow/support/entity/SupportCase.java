package com.rentflow.support.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_cases")
@Getter
@Setter
public class SupportCase extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "opened_by_user_id", nullable = false)
    private UUID openedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportCaseCategory category = SupportCaseCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportCaseStatus status = SupportCaseStatus.OPEN;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

package com.rentflow.tripcondition.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "trip_condition_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_trip_condition_report_actor_type",
                columnNames = {"booking_id", "report_type", "reporter_user_id"}))
@Getter
@Setter
public class TripConditionReport extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "trip_record_id")
    private UUID tripRecordId;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_role", nullable = false, length = 20)
    private TripConditionReporterRole reporterRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private TripConditionReportType reportType;

    @Column(nullable = false)
    private Integer odometer;

    @Column(name = "fuel_level", nullable = false)
    private Integer fuelLevel;

    @Column(name = "exterior_cleanliness", length = 30)
    private String exteriorCleanliness;

    @Column(name = "interior_cleanliness", length = 30)
    private String interiorCleanliness;

    @Column(name = "has_visible_damage", nullable = false)
    private boolean hasVisibleDamage;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

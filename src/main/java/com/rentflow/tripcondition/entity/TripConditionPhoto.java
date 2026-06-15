package com.rentflow.tripcondition.entity;

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
@Table(name = "trip_condition_photos")
@Getter
@Setter
public class TripConditionPhoto extends BaseEntity {

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "file_id", nullable = false, unique = true)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripConditionPhotoAngle angle;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

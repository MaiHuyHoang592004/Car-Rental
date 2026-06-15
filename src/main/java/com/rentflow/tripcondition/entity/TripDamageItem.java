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
@Table(name = "trip_damage_items")
@Getter
@Setter
public class TripDamageItem extends BaseEntity {

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false, length = 80)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripDamageSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_id")
    private UUID photoId;

    @Column(name = "pre_existing", nullable = false)
    private boolean preExisting;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

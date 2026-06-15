package com.rentflow.vehicledocument.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicle_documents")
@Getter
@Setter
public class VehicleDocument extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VehicleDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VehicleDocumentStatus status = VehicleDocumentStatus.PENDING_REVIEW;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "document_number", length = 120)
    private String documentNumber;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

package com.rentflow.vehicledocument.repository;

import com.rentflow.vehicledocument.entity.VehicleDocument;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.entity.VehicleDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    List<VehicleDocument> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);

    Page<VehicleDocument> findByStatusOrderByCreatedAtDesc(VehicleDocumentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM VehicleDocument d WHERE d.id = :id")
    Optional<VehicleDocument> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByVehicleIdAndTypeAndStatusAndExpiresAtGreaterThanEqual(
            UUID vehicleId,
            VehicleDocumentType type,
            VehicleDocumentStatus status,
            LocalDate date);
}
